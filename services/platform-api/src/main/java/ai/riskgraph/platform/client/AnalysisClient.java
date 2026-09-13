package ai.riskgraph.platform.client;

import ai.riskgraph.platform.service.PipelineException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AnalysisClient {
    private final RestClient.Builder builder;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, RestClient> clients = new ConcurrentHashMap<>();
    @Value("${RISKGRAPH_SERVICE_TOKEN:}")
    private String serviceToken = "";
    @Value("${RISKGRAPH_MAX_DEPENDENCY_RESPONSE_BYTES:33554432}")
    private int maxResponseBytes = 32 * 1024 * 1024;
    public AnalysisClient(RestClient.Builder builder, ObjectMapper mapper) {
        this.builder = builder;
        this.mapper = mapper;
    }
    public JsonNode post(String baseUrl, String route, JsonNode body) {
        try {
            return clients.computeIfAbsent(baseUrl,
                    url -> builder.clone().baseUrl(url).build()).post().uri(route)
                .header("X-RiskGraph-Service-Token", serviceToken)
                .contentType(MediaType.APPLICATION_JSON).body(body.toString()).exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new PipelineException("DEPENDENCY_REJECTED", 502,
                            "Analysis dependency rejected the request (HTTP " + response.getStatusCode().value() + ")");
                    }
                    byte[] bytes = response.getBody().readNBytes(maxResponseBytes + 1);
                    if (bytes.length > maxResponseBytes) {
                        throw new PipelineException("DEPENDENCY_RESPONSE_TOO_LARGE", 502, "Dependency result exceeds limit");
                    }
                    try { return mapper.readTree(bytes); }
                    catch (RuntimeException ex) {
                        throw new PipelineException("INVALID_DEPENDENCY_RESPONSE", 502, "Dependency returned invalid JSON");
                    }
                });
        } catch (ResourceAccessException ex) {
            boolean timeout = false;
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                timeout |= cause instanceof java.net.SocketTimeoutException;
            }
            throw new PipelineException(timeout ? "DEPENDENCY_TIMEOUT" : "DEPENDENCY_UNAVAILABLE",
                timeout ? 504 : 503, "Analysis dependency " + (timeout ? "timed out" : "is unavailable"));
        }
    }
}
