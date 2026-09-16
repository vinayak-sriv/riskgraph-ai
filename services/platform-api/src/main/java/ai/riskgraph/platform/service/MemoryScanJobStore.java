package ai.riskgraph.platform.service;

import ai.riskgraph.platform.security.ScanAccessService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class MemoryScanJobStore implements ScanJobStore {
    private final ConcurrentHashMap<UUID, ScanJob> jobs = new ConcurrentHashMap<>();
    private final ScanAccessService access;

    public MemoryScanJobStore(ScanAccessService access) {
        this.access = access;
    }

    @Override
    public synchronized ScanJob createOrReuse(String requestKey, String owner, String repository,
            String oldCommit, String newCommit) {
        ScanJob reusable = jobs.values().stream()
                .filter(job -> job.owner().equals(owner) && job.requestKey().equals(requestKey))
                .filter(job -> job.state() == State.QUEUED || job.state() == State.RUNNING
                        || job.state() == State.COMPLETED)
                .findFirst().orElse(null);
        if (reusable != null) return reusable;
        Instant now = Instant.now();
        ScanJob created = new ScanJob(UUID.randomUUID(), requestKey, owner, repository,
                oldCommit, newCommit, State.QUEUED, "QUEUED", 0, null, null, now, now);
        jobs.put(created.jobId(), created);
        return created;
    }

    @Override
    public ScanJob get(UUID jobId) {
        return jobs.get(jobId);
    }

    @Override
    public synchronized boolean updateIfActive(UUID jobId, State state, String stage,
            int progress, String reasonCode, String scanId) {
        ScanJob current = jobs.get(jobId);
        if (current == null || current.state() == State.CANCELLED) return false;
        jobs.put(jobId, updated(current, state, stage, progress, reasonCode, scanId));
        return true;
    }

    @Override
    public synchronized ScanJob cancel(UUID jobId) {
        ScanJob current = jobs.get(jobId);
        if (current == null || current.state() == State.COMPLETED
                || current.state() == State.FAILED || current.state() == State.CANCELLED) return current;
        ScanJob cancelled = updated(current, State.CANCELLED, "CANCELLED",
                current.progress(), "CANCELLED_BY_USER", current.scanId());
        jobs.put(jobId, cancelled);
        return cancelled;
    }

    @Override
    public List<ScanJob> listVisible(String username, boolean administrator, Instant before,
            UUID beforeId, int limit) {
        return jobs.values().stream().filter(job -> visible(job, username, administrator))
                .filter(job -> before == null || job.updatedAt().isBefore(before)
                        || job.updatedAt().equals(before) && job.jobId().compareTo(beforeId) < 0)
                .sorted(Comparator.comparing(ScanJob::updatedAt).reversed()
                        .thenComparing(ScanJob::jobId, Comparator.reverseOrder()))
                .limit(limit).toList();
    }

    private boolean visible(ScanJob job, String username, boolean administrator) {
        return administrator || job.owner().equals(username)
                || job.scanId() != null && access.canView(job.scanId(), username, false);
    }

    @Override
    public synchronized int failInterruptedJobs() {
        int count = 0;
        for (ScanJob job : List.copyOf(jobs.values())) {
            if (job.state() == State.QUEUED || job.state() == State.RUNNING) {
                jobs.put(job.jobId(), updated(job, State.FAILED, "RECOVERY", job.progress(),
                        "PLATFORM_RESTARTED", job.scanId()));
                count++;
            }
        }
        return count;
    }

    private ScanJob updated(ScanJob value, State state, String stage, int progress,
            String reasonCode, String scanId) {
        return new ScanJob(value.jobId(), value.requestKey(), value.owner(), value.repository(),
                value.oldCommit(), value.newCommit(), state, stage, progress, reasonCode, scanId,
                value.createdAt(), Instant.now());
    }
}
