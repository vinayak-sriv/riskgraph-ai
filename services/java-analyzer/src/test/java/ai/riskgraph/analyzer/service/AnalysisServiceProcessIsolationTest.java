package ai.riskgraph.analyzer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ai.riskgraph.analyzer.extract.SpringEndpointExtractor;
import ai.riskgraph.analyzer.source.GitSourceAcquirer;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AnalysisServiceProcessIsolationTest {
    @Test
    void failsClosedWhenTheConfiguredWorkerArtifactIsUnavailable() {
        AnalysisService service = new AnalysisService(
                mock(GitSourceAcquirer.class), mock(SpringEndpointExtractor.class), Clock.systemUTC(),
                2, 1, 1, true, "missing-analyzer-worker.jar", JsonMapper.builder().build());
        try {
            assertThatThrownBy(() -> service.analyze(Path.of("."), "a".repeat(40), "b".repeat(40)))
                    .isInstanceOf(AnalysisException.class)
                    .extracting(error -> ((AnalysisException) error).code())
                    .isEqualTo("ANALYZER_WORKER_UNAVAILABLE");
        } finally {
            service.shutdown();
        }
    }
}
