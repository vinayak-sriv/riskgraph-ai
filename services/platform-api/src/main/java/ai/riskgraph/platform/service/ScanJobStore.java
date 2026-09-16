package ai.riskgraph.platform.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScanJobStore {
    enum State { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

    ScanJob createOrReuse(String requestKey, String owner, String repository,
            String oldCommit, String newCommit);
    ScanJob get(UUID jobId);
    boolean updateIfActive(UUID jobId, State state, String stage, int progress,
            String reasonCode, String scanId);
    ScanJob cancel(UUID jobId);
    List<ScanJob> listVisible(String username, boolean administrator, Instant before,
            UUID beforeId, int limit);
    int failInterruptedJobs();

    record ScanJob(UUID jobId, String requestKey, String owner, String repository,
            String oldCommit, String newCommit, State state, String stage, int progress,
            String reasonCode, String scanId, Instant createdAt, Instant updatedAt) { }
}
