package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.riskgraph.platform.security.ScanAccessService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AsyncScanJobServiceTest {
    @Test
    void historyCursorPreservesNanosecondsAndUuidTieBreaker() {
        ScanJobStore store = mock(ScanJobStore.class);
        var instant = java.time.Instant.parse("2026-09-17T10:00:00.123456789Z");
        UUID id = UUID.randomUUID();
        var job = new ScanJobStore.ScanJob(id, "key", "analyst", "/repo",
                "1".repeat(40), "2".repeat(40), ScanJobStore.State.COMPLETED,
                "COMPLETED", 100, null, "scan", instant, instant);
        when(store.listVisible("analyst", false, null, null, 2))
                .thenReturn(java.util.List.of(job, job));
        when(store.listVisible("analyst", false, instant, id, 2))
                .thenReturn(java.util.List.of(job));
        AsyncScanJobService service = new AsyncScanJobService(mock(SourceScanService.class),
                store, mock(ScanAccessService.class), 1, 1);
        try {
            String cursor = service.list("analyst", false, null, 1).nextCursor();
            assertThat(cursor).isNotNull();
            service.list("analyst", false, cursor, 1);
            verify(store).listVisible("analyst", false, instant, id, 2);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void completesAndReusesAnIdenticalCanonicalSubmission() throws InterruptedException {
        SourceScanService scans = mock(SourceScanService.class);
        ScanAccessService access = mock(ScanAccessService.class);
        MemoryScanJobStore store = new MemoryScanJobStore(access);
        when(scans.analyze(anyString(), anyString(), anyString())).thenReturn(
                JsonMapper.builder().build().createObjectNode().put("scan_id", "a".repeat(64)));
        AsyncScanJobService service = new AsyncScanJobService(scans, store, access, 1, 1);
        try {
            AsyncScanJobService.Submission first = service.submit("analyst", "/repo",
                    "1".repeat(40), "2".repeat(40));
            verify(access, timeout(2000)).claimFor("a".repeat(64), "analyst");
            ScanJobStore.ScanJob completed = awaitState(store, first.job().jobId(),
                    ScanJobStore.State.COMPLETED);
            assertThat(completed.state()).isEqualTo(ScanJobStore.State.COMPLETED);
            assertThat(completed.scanId()).isEqualTo("a".repeat(64));

            AsyncScanJobService.Submission duplicate = service.submit("analyst", "/repo",
                    "1".repeat(40), "2".repeat(40));
            assertThat(duplicate.reused()).isTrue();
            assertThat(duplicate.job().jobId()).isEqualTo(first.job().jobId());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void cancellationIsTerminalEvenWhenLateAnalysisReturns() throws Exception {
        SourceScanService scans = mock(SourceScanService.class);
        ScanAccessService access = mock(ScanAccessService.class);
        MemoryScanJobStore store = new MemoryScanJobStore(access);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(scans.analyze(anyString(), anyString(), anyString())).thenAnswer(call -> {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return JsonMapper.builder().build().createObjectNode().put("scan_id", "b".repeat(64));
        });
        AsyncScanJobService service = new AsyncScanJobService(scans, store, access, 1, 1);
        try {
            var submission = service.submit("analyst", "/repo", "3".repeat(40), "4".repeat(40));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            ScanJobStore.ScanJob cancelled = service.cancel(submission.job().jobId(), "analyst", false);
            release.countDown();

            assertThat(cancelled.state()).isEqualTo(ScanJobStore.State.CANCELLED);
            assertThat(store.get(submission.job().jobId()).state())
                    .isEqualTo(ScanJobStore.State.CANCELLED);
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    @Test
    void rejectsInvalidHistoryCursorWithoutQueryingTheStore() {
        AsyncScanJobService service = new AsyncScanJobService(mock(SourceScanService.class),
                new MemoryScanJobStore(mock(ScanAccessService.class)),
                mock(ScanAccessService.class), 1, 1);
        try {
            assertThatThrownBy(() -> service.list("analyst", false, "not-a-cursor", 20))
                    .isInstanceOf(PipelineException.class)
                    .extracting(error -> ((PipelineException) error).code)
                    .isEqualTo("INVALID_CURSOR");
        } finally {
            service.shutdown();
        }
    }

    private ScanJobStore.ScanJob awaitState(MemoryScanJobStore store, UUID jobId,
            ScanJobStore.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        ScanJobStore.ScanJob current = store.get(jobId);
        while (current.state() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
            current = store.get(jobId);
        }
        return current;
    }
}
