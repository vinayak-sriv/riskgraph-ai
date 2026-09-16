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
    @Value("${RISKGRAPH_GRAPH_SERVICE_TOKEN:}")
    private String serviceToken = "";
    @Value("${RISKGRAPH_MAX_DEPENDENCY_RESPONSE_BYTES:33554432}")
    private int maxResponseBytes = 32 * 1024 * 1024;

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
            JsonNode response = restClient.post()
                    .uri("/analysis")
                    .header("X-RiskGraph-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request.toString())
                    .exchange((httpRequest, result) -> {
                        if (!result.getStatusCode().is2xxSuccessful()) {
                            throw new ResponseStatusException(BAD_GATEWAY,
                                    "Graph risk service rejected the request");
                        }
                        byte[] bytes = result.getBody().readNBytes(maxResponseBytes + 1);
                        if (bytes.length > maxResponseBytes) {
                            throw new ResponseStatusException(BAD_GATEWAY,
                                    "Graph risk service response exceeds the configured limit");
                        }
                        return objectMapper.readTree(bytes);
                    });
            if (response == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Graph risk service returned no result");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Graph risk service is unavailable",
                    exception
            );
        }
    }
}
