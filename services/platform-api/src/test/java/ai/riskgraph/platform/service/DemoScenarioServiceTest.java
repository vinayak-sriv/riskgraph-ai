package ai.riskgraph.platform.service;

import ai.riskgraph.platform.client.GraphRiskClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class DemoScenarioServiceTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RecordingGraphRiskClient graphRiskClient = new RecordingGraphRiskClient();
    private final DemoScenarioService service = new DemoScenarioService(graphRiskClient, objectMapper);

    @Test
    void sendsCanonicalAuthorizationRemovalFixturesToGraphService() {
        JsonNode result = service.analyzeAuthorizationRemoval();

        JsonNode request = graphRiskClient.request;
        assertThat(request.path("before").size()).isEqualTo(1);
        assertThat(request.path("after").size()).isEqualTo(1);
        assertThat(request.at("/before/0/authentication").asBoolean()).isTrue();
        assertThat(request.at("/before/0/required_role").stringValue()).isEqualTo("ADMIN");
        assertThat(request.at("/after/0/authentication").asBoolean()).isFalse();
        assertThat(request.at("/after/0/required_role").isNull()).isTrue();
        assertThat(result.path("verdict").stringValue()).isEqualTo("BLOCK");
    }

    private final class RecordingGraphRiskClient extends GraphRiskClient {
        private JsonNode request;

        private RecordingGraphRiskClient() {
            super(RestClient.builder(), objectMapper, "http://localhost");
        }

        @Override
        public JsonNode analyze(JsonNode request) {
            this.request = request;
            return objectMapper.createObjectNode().put("verdict", "BLOCK");
        }
    }
}
