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
    @Value("${RISKGRAPH_ANALYZER_SERVICE_TOKEN:}")
    private String analyzerToken = "";
    @Value("${RISKGRAPH_GRAPH_SERVICE_TOKEN:}")
    private String graphToken = "";
    @Value("${RISKGRAPH_AI_SERVICE_TOKEN:}")
    private String aiToken = "";
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
                .header("X-RiskGraph-Service-Token", tokenFor(route))
                .contentType(MediaType.APPLICATION_JSON).body(body.toString()).exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new PipelineException("DEPENDENCY_REJECTED", 502,
                            "Analysis dependency rejected the request (HTTP " + response.getStatusCode().value()
                                + "): " + errorDetail(response));
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

    /** Raw (truncated) response body text, for diagnosability. Deliberately not JSON-parsed:
     * a FastAPI validation error's "detail" is a list, not a string, and every downstream
     * service has its own error shape, so the raw body is the one format that always renders. */
    private String errorDetail(org.springframework.http.client.ClientHttpResponse response) {
        try {
            byte[] bytes = response.getBody().readNBytes(4096);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            return "(no error detail available: " + error.getMessage() + ")";
        }
    }

    private String tokenFor(String route) {
        return switch (route) {
            case "/analyze" -> analyzerToken;
            case "/analysis" -> graphToken;
            case "/ai/analyze", "/validation/http" -> aiToken;
            default -> "";
        };
    }
}
