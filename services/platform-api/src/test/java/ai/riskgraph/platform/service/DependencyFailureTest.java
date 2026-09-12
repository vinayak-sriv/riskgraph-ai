package ai.riskgraph.platform.service;

import ai.riskgraph.platform.api.PipelineErrorHandler;
import ai.riskgraph.platform.client.AnalysisClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class DependencyFailureTest {
    final JsonMapper mapper=JsonMapper.builder().build();

    @Test void dependencyFailureAndInvalidJsonAreStructured() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/invalid", exchange -> {
            exchange.sendResponseHeaders(200,4); exchange.getResponseBody().write("oops".getBytes()); exchange.close();
        });
        server.createContext("/unavailable", exchange -> { exchange.sendResponseHeaders(503,-1); exchange.close(); });
        server.start();
        try {
            var client=new AnalysisClient(RestClient.builder(),mapper);
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            assertThatThrownBy(() -> client.post(url,"/invalid",mapper.createObjectNode()))
                .isInstanceOf(PipelineException.class).hasMessageContaining("invalid JSON");
            assertThatThrownBy(() -> client.post(url,"/unavailable",mapper.createObjectNode()))
                .isInstanceOf(PipelineException.class).hasMessageContaining("HTTP 503");
        } finally { server.stop(0); }
    }

    @Test void timeoutAndDatabaseFailureNeverReturnAllow() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var factory=new SimpleClientHttpRequestFactory(); factory.setReadTimeout(50); factory.setConnectTimeout(500);
            var client=new AnalysisClient(RestClient.builder().requestFactory(factory),mapper);
            assertThatThrownBy(() -> client.post("http://127.0.0.1:"+server.getAddress().getPort(),"/slow",mapper.createObjectNode()))
                .isInstanceOfSatisfying(PipelineException.class, ex -> {
                    assertThat(ex.code).isEqualTo("DEPENDENCY_TIMEOUT"); assertThat(ex.status).isEqualTo(504);
                });
        } finally { server.stop(0); }
        var response=new PipelineErrorHandler().database(new DataAccessResourceFailureException("internal password must not leak"));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().toString()).contains("REVIEW","PERSISTENCE_UNAVAILABLE").doesNotContain("password");
    }
}
