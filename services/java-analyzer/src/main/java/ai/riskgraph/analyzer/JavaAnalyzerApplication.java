package ai.riskgraph.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import ai.riskgraph.analyzer.service.AnalysisService;
import java.nio.file.Path;
import java.util.Arrays;
import tools.jackson.databind.ObjectMapper;

@SpringBootApplication
@EnableScheduling
public class JavaAnalyzerApplication {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--riskgraph-worker".equals(args[0])) {
            runWorker(args);
            return;
        }
        SpringApplication.run(JavaAnalyzerApplication.class, args);
    }

    private static void runWorker(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Invalid analyzer worker arguments");
        try (var context = new SpringApplicationBuilder(JavaAnalyzerApplication.class)
                .web(WebApplicationType.NONE)
                .properties("riskgraph.analyzer.process-isolation=false")
                .run()) {
            var result = context.getBean(AnalysisService.class).analyzeDirect(
                    Path.of(args[1]), args[2], args[3]);
            context.getBean(ObjectMapper.class).writeValue(Path.of(args[4]).toFile(), result);
        }
        Arrays.fill(args, "");
    }
}
