package ai.riskgraph.platform.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder() {
        // SimpleClientHttpRequestFactory is HttpURLConnection-based: no connection
        // pooling, so every one of the 4+ dependency calls per scan paid a fresh
        // TCP handshake. The JDK client pools and keeps connections alive.
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(75));
        return RestClient.builder().requestFactory(factory);
    }
}
