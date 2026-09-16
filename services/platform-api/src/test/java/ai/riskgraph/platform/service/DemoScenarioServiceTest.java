package ai.riskgraph.platform.service;

import ai.riskgraph.platform.client.GraphRiskClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoScenarioServiceTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RecordingGraphRiskClient graphRiskClient = new RecordingGraphRiskClient();
    private final DemoScenarioService service = new DemoScenarioService(graphRiskClient, objectMapper, true);

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

    @Test
    void deployedModeReadsPrecomputedResultWithoutCallingGraphService() {
        var precomputed = new DemoScenarioService(graphRiskClient, objectMapper, false)
                .analyzeScenario("safe-change");

        assertThat(precomputed.path("scenario").asString()).isEqualTo("safe-change");
        assertThat(precomputed.path("mode").asString()).isEqualTo("OFFLINE_FIXTURE");
        assertThat(graphRiskClient.request).isNull();
    }

    @Test
    void localDynamicModeHasABoundedRequestRate() {
        var bounded = new DemoScenarioService(graphRiskClient, objectMapper, true);
        ReflectionTestUtils.setField(bounded, "maxDynamicRequestsPerMinute", 1);

        bounded.analyzeScenario("safe-change");

        assertThatThrownBy(() -> bounded.analyzeScenario("safe-change"))
                .isInstanceOfSatisfying(PipelineException.class,
                        error -> assertThat(error.code).isEqualTo("DEMO_RATE_LIMIT"));
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
