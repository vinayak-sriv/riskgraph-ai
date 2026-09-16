package ai.riskgraph.platform.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class AnalysisClientAudienceTest {
    @Test
    void selectsAnAudienceSpecificCredentialForEveryInternalRoute() throws Exception {
        Map<String, String> received = new LinkedHashMap<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (String route : new String[] {"/analyze", "/analysis", "/ai/analyze", "/validation/http"}) {
            server.createContext(route, exchange -> {
                received.put(exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-RiskGraph-Service-Token"));
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
        }
        server.start();
        try {
            var mapper = JsonMapper.builder().build();
            var client = new AnalysisClient(RestClient.builder(), mapper);
            ReflectionTestUtils.setField(client, "analyzerToken", "analyzer-only");
            ReflectionTestUtils.setField(client, "graphToken", "graph-only");
            ReflectionTestUtils.setField(client, "aiToken", "ai-only");
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            for (String route : new String[] {"/analyze", "/analysis", "/ai/analyze", "/validation/http"}) {
                client.post(baseUrl, route, mapper.createObjectNode());
            }
        } finally {
            server.stop(0);
        }

        assertThat(received).containsEntry("/analyze", "analyzer-only")
                .containsEntry("/analysis", "graph-only")
                .containsEntry("/ai/analyze", "ai-only")
                .containsEntry("/validation/http", "ai-only");
    }
}
