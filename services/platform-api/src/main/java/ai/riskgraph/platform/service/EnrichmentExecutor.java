package ai.riskgraph.platform.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class EnrichmentExecutor {
    private final ThreadPoolExecutor executor;
    private final int workers;
    private final int queueCapacity;

    @Autowired
    public EnrichmentExecutor(
            @Value("${riskgraph.enrichment.workers:4}") int workers,
            @Value("${riskgraph.enrichment.queue-capacity:16}") int queueCapacity) {
        this.workers = Math.max(1, workers);
        this.queueCapacity = Math.max(1, queueCapacity);
        executor = new ThreadPoolExecutor(this.workers, this.workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(this.queueCapacity),
                Thread.ofPlatform().daemon(true).name("riskgraph-enrichment-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public Future<JsonNode> submit(Callable<JsonNode> task) {
        return executor.submit(task);
    }

    public QueueHealth health() {
        return new QueueHealth(workers, executor.getActiveCount(), executor.getQueue().size(),
                queueCapacity, executor.getCompletedTaskCount());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record QueueHealth(int workers, int active, int queued, int queueCapacity,
            long completed) { }
}
