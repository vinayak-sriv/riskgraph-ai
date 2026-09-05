package ai.riskgraph.platform.client;

import ai.riskgraph.platform.config.ClientConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphRiskClientTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void unavailableGraphServiceBecomesBadGateway() {
        GraphRiskClient client = new GraphRiskClient(
                new ClientConfig().restClientBuilder(),
                objectMapper,
                "http://127.0.0.1:1"
        );

        assertThatThrownBy(() -> client.analyze(objectMapper.createObjectNode()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode().value()).isEqualTo(502)
                );
    }
}
