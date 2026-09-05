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
        ObjectNode request = objectMapper.createObjectNode();
        request.putArray("before").add(readFixture("demo/auth-removal-before.json"));
        request.putArray("after").add(readFixture("demo/auth-removal-after.json"));
        return graphRiskClient.analyze(request);
    }

    private JsonNode readFixture(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load demo fixture: " + path, exception);
        }
    }
}
