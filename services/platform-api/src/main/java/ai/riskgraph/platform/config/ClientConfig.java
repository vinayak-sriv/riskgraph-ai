package ai.riskgraph.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
    }
}
