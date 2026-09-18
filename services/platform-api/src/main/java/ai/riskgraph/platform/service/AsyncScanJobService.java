package ai.riskgraph.platform.service;

import ai.riskgraph.platform.security.ScanAccessService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class AsyncScanJobService {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncScanJobService.class);
    private final SourceScanService scans;
    private final ScanJobStore jobs;
    private final ScanAccessService access;
    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<UUID, Future<?>> running = new ConcurrentHashMap<>();

    public AsyncScanJobService(SourceScanService scans, ScanJobStore jobs,
            ScanAccessService access,
            @Value("${riskgraph.scan-jobs.workers:2}") int workers,
            @Value("${riskgraph.scan-jobs.queue-capacity:8}") int queueCapacity) {
        this.scans = scans;
        this.jobs = jobs;
        this.access = access;
        int boundedWorkers = Math.max(1, workers);
        executor = new ThreadPoolExecutor(boundedWorkers, boundedWorkers, 0,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                Thread.ofPlatform().daemon(true).name("riskgraph-scan-job-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PostConstruct
    void recover() {
        int failed = jobs.failInterruptedJobs();
        if (failed > 0) LOG.warn("scan_job_recovery failed_interrupted_jobs={}", failed);
    }

    public Submission submit(String owner, String repository, String oldCommit, String newCommit) {
        String requestKey = digest(repository + "\n" + oldCommit.toLowerCase()
                + "\n" + newCommit.toLowerCase());
        ScanJobStore.ScanJob job = jobs.createOrReuse(requestKey, owner, repository,
                oldCommit.toLowerCase(), newCommit.toLowerCase());
        if (job.state() != ScanJobStore.State.QUEUED || running.containsKey(job.jobId())) {
            return new Submission(job, true);
        }
        try {
            FutureTask<Void> task = new FutureTask<>(() -> {
                run(job);
                return null;
            });
            Future<?> previous = running.putIfAbsent(job.jobId(), task);
            if (previous == null) executor.execute(task);
        } catch (RejectedExecutionException error) {
            running.remove(job.jobId());
            jobs.updateIfActive(job.jobId(), ScanJobStore.State.FAILED, "QUEUE", 0,
                    "SCAN_JOB_QUEUE_SATURATED", null);
            throw new PipelineException("SCAN_JOB_QUEUE_SATURATED", 429,
                    "Scan job capacity is exhausted; retry later");
        }
        return new Submission(jobs.get(job.jobId()), false);
    }

    private void run(ScanJobStore.ScanJob job) {
        try {
            if (!jobs.updateIfActive(job.jobId(), ScanJobStore.State.RUNNING,
                    "ANALYSIS", 10, null, null)) return;
            JsonNode result = scans.analyze(job.repository(), job.oldCommit(), job.newCommit());
            String scanId = result.path("scan_id").asString();
            ScanJobStore.ScanJob current = jobs.get(job.jobId());
            if (current == null || current.state() == ScanJobStore.State.CANCELLED) return;
            access.claimFor(scanId, job.owner());
            jobs.updateIfActive(job.jobId(), ScanJobStore.State.COMPLETED,
                    "COMPLETED", 100, null, scanId);
        } catch (RuntimeException error) {
            String code = error instanceof PipelineException pipeline
                    ? pipeline.code : "SCAN_JOB_FAILED";
            jobs.updateIfActive(job.jobId(), ScanJobStore.State.FAILED,
                    "FAILED", 100, code, null);
            LOG.warn("scan_job_failed job_id={} reason_code={}", job.jobId(), code);
        } finally {
            running.remove(job.jobId());
        }
    }

    public ScanJobStore.ScanJob requireVisible(UUID jobId, String username, boolean administrator) {
        ScanJobStore.ScanJob job = jobs.get(jobId);
        if (job == null || !visible(job, username, administrator)) {
            throw new PipelineException("SCAN_JOB_ACCESS_DENIED", 403,
                    "This scan job is not available to your account");
        }
        return job;
    }

    public ScanJobStore.ScanJob cancel(UUID jobId, String username, boolean administrator) {
        ScanJobStore.ScanJob job = requireVisible(jobId, username, administrator);
        if (!administrator && !job.owner().equals(username)) {
            throw new PipelineException("SCAN_JOB_CANCEL_DENIED", 403,
                    "Only the job owner or an administrator can cancel this job");
        }
        ScanJobStore.ScanJob cancelled = jobs.cancel(jobId);
        Future<?> task = running.remove(jobId);
        if (task != null) task.cancel(true);
        return cancelled;
    }

    public JobPage list(String username, boolean administrator, String cursor, int requestedLimit) {
        Cursor anchor = decodeCursor(cursor);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        List<ScanJobStore.ScanJob> values = jobs.listVisible(username, administrator,
                anchor == null ? null : anchor.updatedAt(), anchor == null ? null : anchor.jobId(),
                limit + 1);
        boolean more = values.size() > limit;
        List<ScanJobStore.ScanJob> page = more ? values.subList(0, limit) : values;
        String next = more ? encodeCursor(page.getLast()) : null;
        return new JobPage(List.copyOf(page), next);
    }

    public JsonNode result(UUID jobId, String username, boolean administrator) {
        ScanJobStore.ScanJob job = requireVisible(jobId, username, administrator);
        if (job.state() != ScanJobStore.State.COMPLETED || job.scanId() == null) {
            throw new PipelineException("SCAN_JOB_NOT_COMPLETED", 409,
                    "The scan job does not have a completed result");
        }
        access.requireView(job.scanId());
        return scans.get(job.scanId());
    }

    private boolean visible(ScanJobStore.ScanJob job, String username, boolean administrator) {
        return administrator || job.owner().equals(username)
                || job.scanId() != null && access.canView(job.scanId(), username, false);
    }

    private String encodeCursor(ScanJobStore.ScanJob job) {
        String plain = job.updatedAt() + "|" + job.jobId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (plain.contains("|")) {
                String[] parts = plain.split("\\|", 2);
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            }
            // Accept previously issued cursors during an upgrade.
            String[] parts = plain.split(":", 2);
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
        } catch (RuntimeException error) {
            throw new PipelineException("INVALID_CURSOR", 400, "The scan history cursor is invalid");
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record Submission(ScanJobStore.ScanJob job, boolean reused) { }
    public record JobPage(List<ScanJobStore.ScanJob> jobs, String nextCursor) { }
    private record Cursor(Instant updatedAt, UUID jobId) { }
}
