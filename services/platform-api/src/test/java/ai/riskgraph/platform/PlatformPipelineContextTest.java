package ai.riskgraph.platform;

import ai.riskgraph.platform.api.DemoScenarioController;
import ai.riskgraph.platform.client.AnalysisClient;
import ai.riskgraph.platform.config.ClientConfig;
import ai.riskgraph.platform.service.DemoScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformPipelineContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("riskgraph.services.graph-risk-base-url=http://localhost:8082");

    @Test
    void applicationContextWiresDemoPipeline() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DemoScenarioController.class);
            assertThat(context).hasSingleBean(DemoScenarioService.class);
            assertThat(context).hasSingleBean(AnalysisClient.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ClientConfig.class, AnalysisClient.class, DemoScenarioService.class, DemoScenarioController.class})
    static class TestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
