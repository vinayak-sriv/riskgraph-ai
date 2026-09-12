package ai.riskgraph.analyzer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import ai.riskgraph.analyzer.extract.SpringEndpointExtractor;
import ai.riskgraph.analyzer.source.GitSourceAcquirer;

class AnalysisServiceTimeoutTest {
    @Test
    void cancelsAnalysisAtConfiguredDeadline() throws Exception {
        GitSourceAcquirer acquirer = mock(GitSourceAcquirer.class);
        CountDownLatch release = new CountDownLatch(1);
        when(acquirer.acquire(any(), anyString(), anyString())).thenAnswer(invocation -> {
            release.await();
            return null;
        });
        AnalysisService service = new AnalysisService(
                acquirer, mock(SpringEndpointExtractor.class), Clock.systemUTC(), 1);

        assertThatThrownBy(() -> service.analyze(java.nio.file.Path.of("."), "a".repeat(40), "b".repeat(40)))
                .isInstanceOf(AnalysisException.class)
                .extracting(error -> ((AnalysisException) error).code())
                .isEqualTo("ANALYSIS_TIMEOUT");
        release.countDown();
    }
}
