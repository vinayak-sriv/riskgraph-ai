package ai.riskgraph.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.riskgraph.analyzer.extract.SpringEndpointExtractor;
import ai.riskgraph.analyzer.source.GitSourceAcquirer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AnalysisServiceCapacityTest {
    @Test
    void rejectsExcessWorkInsteadOfCreatingUnboundedWorkers() throws Exception {
        GitSourceAcquirer acquirer = mock(GitSourceAcquirer.class);
        CountDownLatch release = new CountDownLatch(1);
        when(acquirer.acquire(any(), anyString(), anyString())).thenAnswer(invocation -> {
            release.await();
            throw new AnalysisException("TEST_RELEASE", "released");
        });
        AnalysisService service = new AnalysisService(
                acquirer, mock(SpringEndpointExtractor.class), Clock.systemUTC(), 30, 1, 1);
        var callers = Executors.newFixedThreadPool(2);
        String oldCommit = "a".repeat(40);
        String newCommit = "b".repeat(40);
        try {
            callers.submit(() -> service.analyze(Path.of("."), oldCommit, newCommit));
            callers.submit(() -> service.analyze(Path.of("."), oldCommit, newCommit));
            ThreadPoolExecutor executor = (ThreadPoolExecutor) ReflectionTestUtils.getField(service, "executor");
            for (int attempt = 0; attempt < 100 && executor.getQueue().isEmpty(); attempt++) {
                Thread.sleep(10);
            }
            assertThat(executor.getQueue()).hasSize(1);
            assertThatThrownBy(() -> service.analyze(Path.of("."), oldCommit, newCommit))
                    .isInstanceOf(AnalysisException.class)
                    .extracting(error -> ((AnalysisException) error).code())
                    .isEqualTo("ANALYZER_BUSY");
        } finally {
            release.countDown();
            callers.shutdownNow();
            service.shutdown();
        }
    }
}
