package ai.riskgraph.platform.service;

import java.io.IOException;

import ai.riskgraph.platform.client.GraphRiskClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class DemoScenarioService {
    private final GraphRiskClient graphRiskClient;
    private final ObjectMapper objectMapper;

    public DemoScenarioService(GraphRiskClient graphRiskClient, ObjectMapper objectMapper) {
        this.graphRiskClient = graphRiskClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode analyzeAuthorizationRemoval() {
        return analyzeScenario("authorization-removal");
    }

    public JsonNode analyzeScenario(String scenario) {
        ObjectNode request = objectMapper.createObjectNode();
        ObjectNode protectedRow = (ObjectNode) readFixture("contracts/ir/examples/auth-removal-before.json");
        ObjectNode publicRow = (ObjectNode) readFixture("contracts/ir/examples/auth-removal-after.json");
        var before = request.putArray("before");
        var after = request.putArray("after");
        switch (scenario) {
            case "authorization-removal" -> { before.add(protectedRow); after.add(publicRow); }
            case "safe-change" -> { before.add(protectedRow); after.add(protectedRow.deepCopy()); }
            case "new-public-sensitive-endpoint" -> after.add(publicRow);
            case "sensitive-resource-exposure" -> {
                before.add(publicRow.deepCopy().put("resource", "PublicCatalog").put("sensitivity", "LOW")
                    .put("repository", "CatalogRepository"));
                after.add(publicRow);
            }
            default -> throw new PipelineException("UNKNOWN_SCENARIO", 404, "Unknown demo scenario");
        }
        ObjectNode result = (ObjectNode) graphRiskClient.analyze(request);
        result.put("scenario", scenario).put("mode", "FIXTURE");
        result.put("pre_validation_verdict", result.path("verdict").asString());
        result.put("final_verdict", result.path("verdict").asString()).put("validation_status", "NOT_RUN");
        return result;
    }

    private JsonNode readFixture(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load demo fixture: " + path, exception);
        }
    }
}
