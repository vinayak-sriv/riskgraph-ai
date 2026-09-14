package ai.riskgraph.platform.service;

import ai.riskgraph.platform.client.AnalysisClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FindingEnrichmentService {
    private final AnalysisClient client;
    private final ContractValidator contracts;
    private final ObjectMapper mapper;

    public FindingEnrichmentService(
            AnalysisClient client, ContractValidator contracts, ObjectMapper mapper) {
        this.client = client;
        this.contracts = contracts;
        this.mapper = mapper;
    }

    public void buildFindings(ObjectNode result) {
        ArrayNode findings = result.putArray("findings");
        String scanId = result.path("scan_id").asString();
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
            finding.put("resource", resource)
                    .put("severity", result.at("/risk_result/category_after").asString());
            finding.set("dependency_path", path.deepCopy());
            finding.putArray("evidence").add("Anonymous user can newly reach sensitive resource "
                    + resource + " via " + route[1] + " " + route[2]);
            finding.set("validation", notRunValidation());
        }
    }

    public void enrich(ObjectNode result, String aiUrl, int maxAiFindings, int aiConcurrency) {
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
        int workers = Math.max(1, Math.min(aiConcurrency, Math.max(1, liveCount)));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<JsonNode>> futures = new ArrayList<>(liveCount);
        for (int index = 0; index < liveCount; index++) {
            ObjectNode finding = findings.get(index);
            futures.add(executor.submit(() -> explainFinding(finding, aiUrl)));
        }
        try {
            applyExplanations(result, findings, futures, liveCount);
        } finally {
            executor.shutdownNow();
        }
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
                    ai = futures.get(index).get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    ai = fallbackExplanation(finding, "AI_ENRICHMENT_INTERRUPTED");
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

    private ObjectNode notRunValidation() {
        return mapper.createObjectNode().put("status", "NOT_RUN").put("confirmed", false)
                .put("reason_code", "NOT_RUN").put("sandbox_revision", "source-bound");
    }
}
