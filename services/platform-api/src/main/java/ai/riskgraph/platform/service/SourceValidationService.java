package ai.riskgraph.platform.service;

import ai.riskgraph.platform.client.AnalysisClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class SourceValidationService {
    private static final Pattern SHA256_IMAGE_ID = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private final AnalysisClient client;
    private final ContractValidator contracts;
    private final ObjectMapper mapper;

    public SourceValidationService(AnalysisClient client, ContractValidator contracts, ObjectMapper mapper) {
        this.client = client;
        this.contracts = contracts;
        this.mapper = mapper;
    }

    public ValidationPatch validateSandbox(JsonNode result, String revision, String aiUrl) {
        boolean compatible = false;
        for (JsonNode row : result.at("/source_evidence/after")) {
            compatible |= row.at("/endpoint/endpoint").asString().equals("/admin/export")
                    && row.at("/endpoint/method").asString().equals("GET");
        }
        if (!compatible) {
            throw new PipelineException("UNSUPPORTED_SANDBOX_ROUTE", 400, "No matching shipped sandbox route");
        }
        ObjectNode request = mapper.createObjectNode().put("sandbox_revision", revision);
        JsonNode validation;
        try {
            validation = client.post(aiUrl, "/validation/http", request);
            contracts.validate("validation/sandbox-result.schema.json", validation);
        } catch (PipelineException error) {
            validation = errorResult(error.code, revision);
        }
        // Shipped demonstration evidence never confirms an arbitrary source repository.
        return new ValidationPatch(validation.deepCopy(), Map.of(), null);
    }

    public ValidationPatch validateSource(JsonNode result, String sandboxManifest, String aiUrl) {
        JsonNode registered = registeredScenario(sandboxManifest);
        if (!registered.path("new_commit").equals(result.at("/provenance/new_commit"))
                || !registered.path("old_commit").equals(result.at("/provenance/old_commit"))
                || !normalized(registered, "repository_path").equals(
                        normalized(result.path("provenance"), "repository_path"))) {
            throw new PipelineException("SANDBOX_NOT_REGISTERED", 400,
                    "Scanned repository/commit pair has no registered sandbox");
        }
        String commit = registered.path("new_commit").asString();
        if (result.path("findings").isEmpty()) {
            return new ValidationPatch(null, Map.of(),
                    notRunResult("NO_VALIDATABLE_FINDING"));
        }
        return new ValidationPatch(null, findingValidations(result, commit, aiUrl), null);
    }

    public void apply(ObjectNode result, ValidationPatch patch) {
        if (patch.sandboxDemonstration() != null) {
            result.set("sandbox_demonstration", patch.sandboxDemonstration().deepCopy());
            return;
        }
        if (patch.standaloneValidation() != null) {
            result.set("validation", patch.standaloneValidation().deepCopy());
        }
        for (JsonNode value : result.path("findings")) {
            ObjectNode finding = (ObjectNode) value;
            JsonNode validation = patch.findingValidations().get(
                    finding.path("finding_id").asString());
            if (validation != null) finding.set("validation", validation.deepCopy());
        }
        updateSummary(result);
    }

    public void updateSummary(ObjectNode result) {
        List<JsonNode> validations = new ArrayList<>();
        for (JsonNode finding : result.path("findings")) {
            if (finding.has("validation")) validations.add(finding.path("validation"));
        }
        if (validations.isEmpty() && result.has("validation")) validations.add(result.path("validation"));
        if (validations.isEmpty()) return;
        String status = aggregateStatus(validations);
        if (validations.size() == 1) {
            result.set("validation", validations.getFirst().deepCopy());
        } else {
            result.set("validation", aggregateResult(result, validations, status));
        }
        result.put("validation_status", status);
        boolean anyConfirmed = validations.stream()
                .anyMatch(value -> "CONFIRMED".equals(value.path("status").asString()));
        result.put("final_verdict", DecisionPolicy.finalVerdict(
                result.path("pre_validation_verdict").asString(), anyConfirmed ? "CONFIRMED" : status));
        if (validations.stream().anyMatch(value -> Set.of("ERROR", "INCONCLUSIVE")
                .contains(value.path("status").asString()))) {
            result.put("status", "DEGRADED");
        }
    }

    private Map<String, JsonNode> findingValidations(JsonNode result, String commit, String aiUrl) {
        Map<String, JsonNode> routeResults = new HashMap<>();
        Map<String, JsonNode> findingResults = new HashMap<>();
        for (JsonNode value : result.path("findings")) {
            JsonNode finding = value;
            String method = finding.path("method").asString();
            String path = finding.path("path").asString();
            String routeKey = method + " " + path;
            JsonNode validation = method.equals("GET") && path.equals("/admin/export")
                    ? routeResults.computeIfAbsent(routeKey,
                            ignored -> runSourceValidation(commit, method, path, aiUrl)).deepCopy()
                    : notRunResult("UNSUPPORTED_SOURCE_VALIDATION");
            findingResults.put(finding.path("finding_id").asString(), validation.deepCopy());
        }
        return Map.copyOf(findingResults);
    }

    private JsonNode runSourceValidation(String commit, String method, String path, String aiUrl) {
        try {
            JsonNode validation = client.post(aiUrl, "/validation/http", mapper.createObjectNode()
                    .put("sandbox_revision", "vulnerable").put("expected_commit", commit)
                    .put("method", method).put("path", path));
            contracts.validate("validation/sandbox-result.schema.json", validation);
            String status = validation.path("status").asString();
            if (Set.of("CONFIRMED", "REJECTED", "INCONCLUSIVE").contains(status)
                    && (!validation.path("sandbox_revision").asString().equals("vulnerable")
                    || !validation.path("source_commit").asString().equals(commit)
                    || status.equals("CONFIRMED") != validation.path("confirmed").asBoolean()
                    || status.equals("CONFIRMED") && !hasImmutableConfirmationEvidence(validation))) {
                throw new PipelineException("SANDBOX_IDENTITY_MISMATCH", 502,
                        "Sandbox evidence identity mismatch");
            }
            return validation;
        } catch (PipelineException error) {
            return errorResult(error.code, "vulnerable");
        }
    }

    private boolean hasImmutableConfirmationEvidence(JsonNode validation) {
        return SHA256_IMAGE_ID.matcher(validation.path("container_image_id").asString()).matches()
                && SHA256_IMAGE_ID.matcher(validation.path("probe_image_id").asString()).matches()
                && SHA256_HEX.matcher(validation.path("response_sha256").asString()).matches();
    }

    private JsonNode registeredScenario(String manifest) {
        try {
            return mapper.readTree(Files.readString(Path.of(manifest))).at("/scenarios/authorization-removal");
        } catch (Exception error) {
            throw new PipelineException("SANDBOX_NOT_REGISTERED", 400,
                    "Build and register the authored source sandbox first");
        }
    }

    private Path normalized(JsonNode node, String field) {
        return Path.of(node.path(field).asString()).normalize();
    }

    private ObjectNode aggregateResult(ObjectNode result, List<JsonNode> validations, String status) {
        // Unknown cleanup is not completed cleanup; every producer sets the field.
        boolean cleanupComplete = validations.stream()
                .allMatch(value -> value.path("cleanup_complete").asBoolean(false));
        ObjectNode summary = mapper.createObjectNode().put("status", status)
                .put("confirmed", status.equals("CONFIRMED"))
                .put("reason_code", "AGGREGATED_FINDING_VALIDATIONS")
                .put("sandbox_revision", "source-bound").put("cleanup_complete", cleanupComplete);
        var observedHttpStatuses = validations.stream()
                .filter(value -> value.path("actual_status").isInt())
                .map(value -> value.path("actual_status").asInt())
                .distinct().sorted().toList();
        var statusArray = summary.putArray("observed_http_statuses");
        observedHttpStatuses.forEach(statusArray::add);
        if (result.at("/provenance/new_commit").isString()) {
            summary.put("source_commit", result.at("/provenance/new_commit").asString());
        }
        var evidence = summary.putArray("evidence");
        evidence.add("Summary of " + validations.size() + " finding validations");
        for (JsonNode finding : result.path("findings")) {
            if (finding.at("/validation/status").asString().equals("CONFIRMED")) {
                evidence.add("Runtime confirmed anonymous " + finding.path("method").asString()
                        + " " + finding.path("path").asString()
                        + " in the registered source-bound sandbox");
            }
        }
        return summary;
    }

    private String aggregateStatus(List<JsonNode> validations) {
        Set<String> statuses = validations.stream()
                .map(value -> value.path("status").asString("NOT_RUN")).collect(Collectors.toSet());
        if (statuses.size() == 1 && statuses.contains("CONFIRMED")) return "CONFIRMED";
        if (statuses.contains("CONFIRMED")) return "INCONCLUSIVE";
        if (statuses.contains("ERROR")) return "ERROR";
        if (statuses.contains("INCONCLUSIVE")) return "INCONCLUSIVE";
        if (statuses.size() == 1 && statuses.contains("REJECTED")) return "REJECTED";
        if (statuses.size() == 1 && statuses.contains("NOT_RUN")) return "NOT_RUN";
        return "INCONCLUSIVE";
    }

    private ObjectNode errorResult(String reasonCode, String revision) {
        return mapper.createObjectNode().put("status", "ERROR").put("confirmed", false)
                .put("reason_code", reasonCode).put("sandbox_revision", revision)
                .put("cleanup_complete", false);
    }

    private ObjectNode notRunResult(String reasonCode) {
        return mapper.createObjectNode().put("status", "NOT_RUN").put("confirmed", false)
                .put("reason_code", reasonCode).put("sandbox_revision", "source-bound");
    }

    public record ValidationPatch(
            JsonNode sandboxDemonstration,
            Map<String, JsonNode> findingValidations,
            JsonNode standaloneValidation) {
    }
}
