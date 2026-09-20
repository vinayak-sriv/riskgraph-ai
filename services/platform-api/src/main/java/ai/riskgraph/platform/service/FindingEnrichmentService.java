package ai.riskgraph.platform.service;

import ai.riskgraph.platform.client.AnalysisClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.HashSet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FindingEnrichmentService {
    private static final long AI_ENRICHMENT_TIMEOUT_SECONDS = 20;
    private final AnalysisClient client;
    private final ContractValidator contracts;
    private final ObjectMapper mapper;
    private final EnrichmentExecutor executor;

    public FindingEnrichmentService(
            AnalysisClient client, ContractValidator contracts, ObjectMapper mapper) {
        this(client, contracts, mapper, new EnrichmentExecutor(4, 16));
    }

    @Autowired
    public FindingEnrichmentService(AnalysisClient client, ContractValidator contracts,
            ObjectMapper mapper, EnrichmentExecutor executor) {
        this.client = client;
        this.contracts = contracts;
        this.mapper = mapper;
        this.executor = executor;
    }

    public void buildFindings(ObjectNode result) {
        ArrayNode findings = result.putArray("findings");
        String scanId = result.path("scan_id").asString();
        var ambiguousRoutes = new HashSet<String>();
        var risks = new HashMap<FindingKey, JsonNode>();
        for (JsonNode risk : result.at("/risk_result/finding_results")) {
            risks.putIfAbsent(new FindingKey(risk.path("route_id").asString(),
                    risk.path("resource_id").asString()), risk);
        }
        var handlersByFinding = indexHandlerReferences(result);
        for (JsonNode path : result.at("/graph_delta/new_paths")) {
            if (path.path("nodes").size() < 2) continue;
            String routeId = path.at("/nodes/1").asString();
            String[] route = routeId.split(":", 3);
            if (route.length != 3 || !route[0].equals("endpoint")) continue;
            String resource = path.path("target").asString().replaceFirst("^resource:", "");
            ObjectNode finding = findings.addObject();
            finding.put("finding_id", SourceScanService.digest(
                    scanId + "\n" + routeId + "\n" + resource + "\n" + path));
            finding.put("route_id", routeId).put("method", route[1]).put("path", route[2]);
            finding.put("resource", resource);
            FindingKey key = new FindingKey(routeId, path.path("target").asString());
            JsonNode findingRisk = risks.getOrDefault(key, mapper.createObjectNode());
            finding.put("severity", findingRisk.path("category_after").asString("MEDIUM"));
            finding.set("risk_result", findingRisk.deepCopy());
            boolean validationSupported = route[1].equals("GET")
                    && route[2].equals("/admin/export");
            finding.put("validation_capability",
                    validationSupported ? "SUPPORTED" : "UNSUPPORTED");
            finding.set("dependency_path", path.deepCopy());
            ArrayNode evidence = finding.putArray("evidence");
            findingRisk.path("evidence").forEach(item -> evidence.add(item.asString()));
            if (evidence.isEmpty()) {
                evidence.add("Anonymous user can newly reach sensitive resource "
                        + resource + " via " + route[1] + " " + route[2]);
            }
            ArrayNode handlers = finding.putArray("handler_refs");
            for (JsonNode handler : handlersByFinding.getOrDefault(key, List.of())) {
                handlers.add(handler.deepCopy());
            }
            if (handlers.size() > 1) ambiguousRoutes.add(routeId);
            finding.set("validation", notRunValidation(validationSupported));
        }
        for (String routeId : ambiguousRoutes) {
            ((ArrayNode) result.path("diagnostics")).addObject()
                    .put("severity", "WARNING")
                    .put("code", "AMBIGUOUS_ROUTE_HANDLERS")
                    .put("message", "Multiple source handlers resolve to " + routeId)
                    .putNull("path");
            result.put("status", "DEGRADED");
            ((ObjectNode) result.path("quality")).put("incomplete", true);
            if (result.at("/quality/confidence").asString().equals("HIGH")) {
                ((ObjectNode) result.path("quality")).put("confidence", "MEDIUM");
            }
        }
    }

    private java.util.Map<FindingKey, List<JsonNode>> indexHandlerReferences(JsonNode result) {
        var index = new HashMap<FindingKey, List<JsonNode>>();
        for (JsonNode source : result.at("/source_evidence/after")) {
            String route = "endpoint:" + source.at("/endpoint/method").asString()
                    + ":" + source.at("/endpoint/endpoint").asString();
            var resources = new HashSet<String>();
            resources.add(source.at("/endpoint/resource").asString());
            for (JsonNode dependency : source.path("dependency_paths")) {
                resources.add(dependency.path("resource").asString());
            }
            ObjectNode handler = mapper.createObjectNode();
            String controller = source.path("qualified_controller").asString();
            String signature = source.path("method_signature").asString();
            String location = source.at("/source_location/path").asString();
            String repository = result.at("/provenance/repository_identity").asString();
            handler.put("handler_id", SourceScanService.digest(
                    repository + "\n" + controller + "\n" + signature + "\n" + location));
            handler.put("qualified_controller", controller);
            handler.put("method_signature", signature);
            handler.set("source_location", source.path("source_location").deepCopy());
            for (String resource : resources) {
                index.computeIfAbsent(new FindingKey(route, "resource:" + resource),
                        ignored -> new ArrayList<>()).add(handler);
            }
        }
        return index;
    }

    private record FindingKey(String routeId, String resourceId) { }

    public void enrich(ObjectNode result, String aiUrl, int maxAiFindings) {
        List<ObjectNode> findings = new ArrayList<>();
        result.path("findings").forEach(value -> {
            if (!value.has("ai")) findings.add((ObjectNode) value);
        });
        int liveCount = Math.min(Math.max(0, maxAiFindings), findings.size());
        if (liveCount == 0) {
            applyExplanations(result, findings, List.of(), 0);
            finishEnrichment(result);
            return;
        }
        List<Future<JsonNode>> futures = new ArrayList<>(liveCount);
        for (int index = 0; index < liveCount; index++) {
            ObjectNode finding = findings.get(index);
            try {
                futures.add(executor.submit(() -> explainFinding(finding, aiUrl)));
            } catch (RejectedExecutionException error) {
                futures.add(java.util.concurrent.CompletableFuture.completedFuture(
                        fallbackExplanation(finding, "AI_ENRICHMENT_SATURATED")));
            }
        }
        applyExplanations(result, findings, futures, liveCount);
        finishEnrichment(result);
    }

    private void finishEnrichment(ObjectNode result) {
        for (JsonNode finding : result.path("findings")) {
            if (finding.at("/ai/status").asString().equals("DEGRADED")) {
                result.put("status", "DEGRADED");
            }
        }
        if (!result.path("findings").isEmpty()) {
            result.set("ai", result.at("/findings/0/ai").deepCopy());
        }
    }

    private void applyExplanations(ObjectNode result, List<ObjectNode> findings,
            List<Future<JsonNode>> futures, int liveCount) {
        for (int index = 0; index < findings.size(); index++) {
            ObjectNode finding = findings.get(index);
            JsonNode ai;
            if (index >= liveCount) {
                ai = fallbackExplanation(finding, "AI_FINDING_LIMIT");
            } else {
                try {
                    // Bounded: enrich() runs on one of only a couple of scan-job
                    // workers, and an untimed get() inherits the 75s HTTP read
                    // timeout, so two slow AI calls stall the whole pipeline.
                    ai = futures.get(index).get(AI_ENRICHMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    ai = fallbackExplanation(finding, "AI_ENRICHMENT_INTERRUPTED");
                } catch (TimeoutException error) {
                    futures.get(index).cancel(true);
                    ai = fallbackExplanation(finding, "AI_ENRICHMENT_TIMEOUT");
                } catch (ExecutionException error) {
                    ai = fallbackExplanation(finding, "AI_ENRICHMENT_FAILED");
                }
            }
            finding.set("ai", ai);
            if (ai.path("status").asString().equals("DEGRADED")) result.put("status", "DEGRADED");
        }
    }

    private JsonNode explainFinding(ObjectNode finding, String aiUrl) {
        ObjectNode request = mapper.createObjectNode();
        request.set("evidence", finding.path("evidence"));
        request.put("method", finding.path("method").asString());
        request.put("path", finding.path("path").asString());
        try {
            JsonNode ai = client.post(aiUrl, "/ai/analyze", request);
            contracts.validate("ai/explanation.schema.json", ai);
            return ai;
        } catch (PipelineException error) {
            return fallbackExplanation(finding, error.code);
        }
    }

    private ObjectNode fallbackExplanation(JsonNode finding, String reasonCode) {
        ObjectNode fallback = mapper.createObjectNode().put("status", "DEGRADED")
                .put("confirmed", false).put("provider", "deterministic-fallback")
                .put("reason_code", reasonCode);
        ObjectNode analysis = fallback.putObject("analysis")
                .put("finding", "Security change requires review")
                .put("hypothesis", "Unconfirmed: review deterministic evidence and validate in the local Docker sandbox")
                .put("recommended_test", finding.path("method").asString() + " "
                        + finding.path("path").asString() + " without authentication")
                .put("confidence", "LOW");
        analysis.set("evidence", finding.path("evidence").deepCopy());
        return fallback;
    }

    public void mergePreviousEnrichment(ObjectNode result, JsonNode previous) {
        var previousFindings = new HashMap<String, JsonNode>();
        for (JsonNode finding : previous.path("findings")) {
            previousFindings.put(finding.path("finding_id").asString(), finding);
        }
        for (JsonNode value : result.path("findings")) {
            ObjectNode finding = (ObjectNode) value;
            JsonNode old = previousFindings.get(finding.path("finding_id").asString());
            if (old != null && old.has("ai")) {
                finding.set("ai", old.path("ai").deepCopy());
            }
            if (old != null && old.has("validation")) {
                finding.set("validation", old.path("validation").deepCopy());
            }
        }
        if (result.path("findings").isEmpty() && previous.has("validation")) {
            result.set("validation", previous.path("validation").deepCopy());
        }
        if (previous.has("sandbox_demonstration")) {
            result.set("sandbox_demonstration", previous.path("sandbox_demonstration").deepCopy());
        }
    }

    private ObjectNode notRunValidation(boolean supported) {
        return mapper.createObjectNode().put("status", "NOT_RUN").put("confirmed", false)
                .put("reason_code", supported ? "NOT_RUN" : "UNSUPPORTED_SOURCE_VALIDATION")
                .put("sandbox_revision", "source-bound")
                .put("cleanup_complete", true);
    }
}
