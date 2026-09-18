package ai.riskgraph.platform.service;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import ai.riskgraph.platform.client.AnalysisClient;

@Service
public class SourceScanService {
    private final AnalysisClient client;
    private final ContractValidator contracts;
    private final ObjectMapper mapper;
    private final String analyzerUrl;
    private final String graphUrl;
    private final String pythonAnalyzerUrl;
    private final String allowedRoots;
    private final RepositoryFrameworkDetector frameworks = new RepositoryFrameworkDetector();
    @Value("${AI_VALIDATION_BASE_URL:http://localhost:8083}")
    private String aiUrl = "http://localhost:8083";
    @Value("${RISKGRAPH_SANDBOX_MANIFEST:./samples/generated/mvp-v2/manifest.json}")
    private String sandboxManifest = "./samples/generated/mvp-v2/manifest.json";
    @Value("${RISKGRAPH_MAX_AI_FINDINGS:4}")
    private int maxAiFindings = 4;
    private final ScanStore store;
    private final FindingEnrichmentService findings;
    private final SourceValidationService validations;
    private final CanonicalGraphInputBuilder graphInputs;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> scanFlights = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> validationFlights = new ConcurrentHashMap<>();
    private final Object[] storeLocks = createStoreLocks();

    public SourceScanService(AnalysisClient client, ContractValidator contracts, ObjectMapper mapper,
        @Value("${JAVA_ANALYZER_BASE_URL:http://localhost:8081}") String analyzerUrl,
        @Value("${GRAPH_RISK_BASE_URL:http://localhost:8082}") String graphUrl,
        @Value("${RISKGRAPH_ALLOWED_REPOSITORY_ROOTS:./samples/generated}") String allowedRoots, ScanStore store) {
        this(client, contracts, mapper, analyzerUrl, graphUrl, "", allowedRoots, store,
                new FindingEnrichmentService(client, contracts, mapper),
                new SourceValidationService(client, contracts, mapper),
                new CanonicalGraphInputBuilder(mapper));
    }

    @Autowired
    public SourceScanService(AnalysisClient client, ContractValidator contracts, ObjectMapper mapper,
        @Value("${JAVA_ANALYZER_BASE_URL:http://localhost:8081}") String analyzerUrl,
        @Value("${GRAPH_RISK_BASE_URL:http://localhost:8082}") String graphUrl,
        @Value("${PYTHON_ANALYZER_BASE_URL:}") String pythonAnalyzerUrl,
        @Value("${RISKGRAPH_ALLOWED_REPOSITORY_ROOTS:./samples/generated}") String allowedRoots,
        ScanStore store, FindingEnrichmentService findings, SourceValidationService validations,
        CanonicalGraphInputBuilder graphInputs) {
        this.client = client; this.contracts = contracts; this.mapper = mapper;
        this.analyzerUrl = analyzerUrl; this.graphUrl = graphUrl;
        this.pythonAnalyzerUrl = pythonAnalyzerUrl; this.allowedRoots = allowedRoots;
        this.store = store; this.findings = findings; this.validations = validations;
        this.graphInputs = graphInputs;
    }

    SourceScanService(AnalysisClient client, ContractValidator contracts, ObjectMapper mapper,
        String analyzerUrl, String graphUrl, String allowedRoots, ScanStore store,
        FindingEnrichmentService findings, SourceValidationService validations) {
        this(client, contracts, mapper, analyzerUrl, graphUrl, "", allowedRoots, store, findings,
                validations, new CanonicalGraphInputBuilder(mapper));
    }

    SourceScanService(AnalysisClient client, ContractValidator contracts, ObjectMapper mapper,
        String analyzerUrl, String graphUrl, String pythonAnalyzerUrl, String allowedRoots, ScanStore store,
        FindingEnrichmentService findings, SourceValidationService validations) {
        this(client, contracts, mapper, analyzerUrl, graphUrl, pythonAnalyzerUrl, allowedRoots, store,
                findings, validations, new CanonicalGraphInputBuilder(mapper));
    }

    public JsonNode analyze(String repositoryPath, String oldCommit, String newCommit) {
        Path path;
        try { path = Path.of(repositoryPath).toRealPath(); }
        catch (Exception ex) { throw new PipelineException("REPOSITORY_NOT_FOUND", 400, "Repository path does not exist"); }
        boolean allowed = Arrays.stream(allowedRoots.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
            .filter(root -> !root.isBlank()).anyMatch(root -> {
                try { return path.startsWith(Path.of(root).toRealPath()); } catch (Exception ex) { return false; }
            });
        if (!allowed) throw new PipelineException("REPOSITORY_NOT_ALLOWED", 403, "Repository is outside the allowlist");
        if (!oldCommit.matches("[0-9a-fA-F]{40}") || !newCommit.matches("[0-9a-fA-F]{40}"))
            throw new PipelineException("INVALID_COMMIT", 400, "Use full immutable commit SHAs");
        String requestKey = digest(path + "\n" + oldCommit.toLowerCase() + "\n" + newCommit.toLowerCase());
        return singleFlight(scanFlights, requestKey,
                () -> analyzeResolved(path, oldCommit.toLowerCase(), newCommit.toLowerCase()));
    }

    private JsonNode analyzeResolved(Path path, String oldCommit, String newCommit) {
        ObjectNode request = mapper.createObjectNode().put("repository_path", path.toString())
            .put("old_commit", oldCommit).put("new_commit", newCommit);
        // Additive only: Java is the unconditional default (today's only behavior), and a
        // positively detected FastAPI repository is the sole case that redirects elsewhere.
        String targetAnalyzerUrl = analyzerUrl;
        if (frameworks.detect(path) == RepositoryFrameworkDetector.Framework.PYTHON_FASTAPI) {
            if (pythonAnalyzerUrl.isBlank()) {
                throw new PipelineException("PYTHON_ANALYZER_NOT_CONFIGURED", 503,
                    "This repository looks like a Python/FastAPI project, but the Python analyzer is not configured");
            }
            targetAnalyzerUrl = pythonAnalyzerUrl;
        }
        JsonNode envelope = client.post(targetAnalyzerUrl, "/analyze", request);
        contracts.validate("ir/analysis-envelope.schema.json", envelope);
        if (!envelope.at("/provenance/old_commit").asString().equals(oldCommit.toLowerCase())
            || !envelope.at("/provenance/new_commit").asString().equals(newCommit.toLowerCase())
            || !Path.of(envelope.at("/provenance/repository_path").asString()).normalize().equals(path))
            throw new PipelineException("PROVENANCE_MISMATCH", 502, "Analyzer provenance does not match request");
        CanonicalGraphInputBuilder.BuildResult build = graphInputs.build(envelope);
        ObjectNode graphInput = build.graphInput();
        String confidence = build.confidence();
        boolean incomplete = build.incomplete();
        ObjectNode result = ((ObjectNode) client.post(graphUrl, "/analysis", graphInput)).deepCopy();
        contracts.validate("ir/graph-analysis.schema.json", result);
        String graphConfidence = result.at("/quality/confidence").asString(confidence);
        if (graphConfidence.equals("LOW")
                || (graphConfidence.equals("MEDIUM") && confidence.equals("HIGH"))) {
            confidence = graphConfidence;
        }
        incomplete |= result.at("/quality/incomplete").asBoolean()
                || !graphConfidence.equals("HIGH");
        result.put("schema_version", "1.2.0").put("scenario", "local-source");
        result.set("provenance", envelope.path("provenance"));
        result.set("coverage", envelope.path("coverage"));
        ArrayNode diagnostics = (ArrayNode) envelope.path("diagnostics").deepCopy();
        boolean unresolvedAuthorizationDelta = false;
        for (JsonNode reason : result.path("reason_codes")) {
            unresolvedAuthorizationDelta |= reason.asString()
                    .equals("UNRESOLVED_AUTHORIZATION_DELTA");
        }
        if (unresolvedAuthorizationDelta) {
            diagnostics.addObject()
                    .put("severity", "WARNING")
                    .put("code", "UNRESOLVED_AUTHORIZATION_DELTA")
                    .put("message", "Authorization roles changed, but role ordering is outside the annotation-only MVP")
                    .putNull("path");
        }
        result.set("diagnostics", diagnostics);
        result.set("source_evidence", mapper.createObjectNode().set("before", envelope.path("before")));
        ((ObjectNode) result.path("source_evidence")).set("after", envelope.path("after"));
        result.put("analyzer_version", envelope.path("analyzer_version").asString());
        result.put("analyzer_config_hash", envelope.path("analyzer_config_hash").asString());
        result.put("analysis_id", envelope.path("analysis_id").asString());
        result.put("pre_validation_verdict", result.path("verdict").asString());
        result.put("validation_status", "NOT_RUN").put("final_verdict", result.path("verdict").asString());
        result.put("status", incomplete || !confidence.equals("HIGH") ? "DEGRADED" : "COMPLETE");
        result.put("scan_id", ScanIdentityFactory.create(mapper, envelope, graphInput, result));
        findings.buildFindings(result);
        String scanId = result.path("scan_id").asString();
        JsonNode previous = store.get(scanId);
        if (previous != null) findings.mergePreviousEnrichment(result, previous);
        findings.enrich(result, aiUrl, maxAiFindings);
        synchronized (storeLockFor(scanId)) {
            ObjectNode prepared = result;
            JsonNode updated = store.update(scanId, latest -> {
                findings.mergePreviousEnrichment(prepared, latest);
                validations.updateSummary(prepared);
                return prepared;
            });
            if (updated == null) {
                store.save(result);
            } else {
                result = (ObjectNode) updated;
            }
        }
        LoggerFactory.getLogger(getClass()).info("scan_completed analysis_id={} verdict={}",
            result.path("analysis_id").asString(), result.path("verdict").asString());
        return result;
    }

    private static Object[] createStoreLocks() {
        Object[] locks = new Object[256];
        java.util.Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    private Object storeLockFor(String scanId) {
        return storeLocks[(scanId.hashCode() & Integer.MAX_VALUE) % storeLocks.length];
    }

    private JsonNode singleFlight(
            ConcurrentHashMap<String, CompletableFuture<JsonNode>> flights,
            String key,
            Supplier<JsonNode> operation) {
        CompletableFuture<JsonNode> created = new CompletableFuture<>();
        CompletableFuture<JsonNode> existing = flights.putIfAbsent(key, created);
        if (existing != null) return await(existing);
        try {
            JsonNode result = operation.get();
            created.complete(result.deepCopy());
            return result;
        } catch (RuntimeException error) {
            created.completeExceptionally(error);
            throw error;
        } finally {
            flights.remove(key, created);
        }
    }

    private JsonNode await(CompletableFuture<JsonNode> future) {
        try {
            return future.join().deepCopy();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    public JsonNode validateSandbox(String id, String revision) {
        if (!java.util.Set.of("protected", "vulnerable").contains(revision))
            throw new PipelineException("INVALID_SANDBOX", 400, "Select a shipped sandbox revision");
        return singleFlight(validationFlights, "demo:" + id + ":" + revision,
                () -> saveValidated(id, result -> validations.validateSandbox(result, revision, aiUrl)));
    }

    public JsonNode validateSource(String id) {
        return singleFlight(validationFlights, "source:" + id,
                () -> saveValidated(id, result -> validations.validateSource(result, sandboxManifest, aiUrl)));
    }

    private JsonNode saveValidated(
            String id,
            Function<JsonNode, SourceValidationService.ValidationPatch> validation) {
        JsonNode snapshot = get(id);
        SourceValidationService.ValidationPatch patch = validation.apply(snapshot);
        JsonNode updated = store.update(id, latest -> {
            validations.apply(latest, patch);
            return latest;
        });
        if (updated == null) throw new PipelineException("SCAN_NOT_FOUND", 404, "Scan does not exist");
        return updated;
    }

    public JsonNode get(String id) {
        JsonNode result = store.get(id);
        if (result == null) throw new PipelineException("SCAN_NOT_FOUND", 404, "Scan does not exist");
        return result.deepCopy();
    }

    public java.util.Map<String, ScanStore.Summary> summaries(java.util.Collection<String> ids) {
        return store.summaries(ids);
    }

    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
