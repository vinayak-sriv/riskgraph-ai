package ai.riskgraph.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import ai.riskgraph.platform.client.AnalysisClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EnrichmentExecutorTest {
    @Test
    void boundsWorkersAndQueueAndReportsHealth() throws Exception {
        EnrichmentExecutor executor = new EnrichmentExecutor(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return JsonMapper.builder().build().createObjectNode();
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> JsonMapper.builder().build().createObjectNode());

            assertThatThrownBy(() -> executor.submit(
                    () -> JsonMapper.builder().build().createObjectNode()))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(executor.health().workers()).isEqualTo(1);
            assertThat(executor.health().active()).isEqualTo(1);
            assertThat(executor.health().queued()).isEqualTo(1);
            assertThat(executor.health().queueCapacity()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void simultaneousScansShareTheSameDownstreamConcurrencyLimit() throws Exception {
        EnrichmentExecutor executor = new EnrichmentExecutor(1, 1);
        AnalysisClient client = mock(AnalysisClient.class);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.post(any(), any(), any())).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            active.decrementAndGet();
            return JsonMapper.builder().build().createObjectNode().put("status", "COMPLETE");
        });
        FindingEnrichmentService service = new FindingEnrichmentService(client,
                mock(ContractValidator.class), JsonMapper.builder().build(), executor);
        ObjectNode first = scanWithFindings(2);
        ObjectNode second = scanWithFindings(2);
        try {
            CompletableFuture<Void> firstScan = CompletableFuture.runAsync(
                    () -> service.enrich(first, "ai", 2));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> secondScan = CompletableFuture.runAsync(
                    () -> service.enrich(second, "ai", 2));
            secondScan.get(2, TimeUnit.SECONDS);

            assertThat(peak).hasValue(1);
            assertThat(second.at("/findings/0/ai/reason_code").asString())
                    .isEqualTo("AI_ENRICHMENT_SATURATED");
            assertThat(second.at("/findings/1/ai/reason_code").asString())
                    .isEqualTo("AI_ENRICHMENT_SATURATED");
            release.countDown();
            firstScan.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private ObjectNode scanWithFindings(int count) {
        ObjectNode scan = JsonMapper.builder().build().createObjectNode().put("status", "COMPLETE");
        var findings = scan.putArray("findings");
        for (int index = 0; index < count; index++) {
            findings.addObject().put("method", "GET").put("path", "/resource/" + index)
                    .putArray("evidence").add("deterministic evidence");
        }
        return scan;
    }
}
