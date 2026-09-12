package ai.riskgraph.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] dashboardOrigins;

    public WebConfig(@Value("${riskgraph.web.allowed-origins:${riskgraph.web.allowed-origin},http://127.0.0.1:5173}") String dashboardOrigins) {
        this.dashboardOrigins = java.util.Arrays.stream(dashboardOrigins.split(","))
            .map(String::strip).filter(value -> !value.isBlank()).distinct().toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(dashboardOrigins).allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-CSRF-TOKEN").allowCredentials(true);
    }
}
