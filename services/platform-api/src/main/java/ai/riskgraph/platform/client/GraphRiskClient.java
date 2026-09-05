package ai.riskgraph.platform.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class GraphRiskClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GraphRiskClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${riskgraph.services.graph-risk-base-url}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public JsonNode analyze(JsonNode request) {
        try {
            String response = restClient.post()
                    .uri("/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request.toString())
                    .retrieve()
                    .body(String.class);
            if (response == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Graph risk service returned no result");
            }
            return objectMapper.readTree(response);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Graph risk service is unavailable",
                    exception
            );
        }
    }
}
