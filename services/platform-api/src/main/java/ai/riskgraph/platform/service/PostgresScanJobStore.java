package ai.riskgraph.platform.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!local")
public class PostgresScanJobStore implements ScanJobStore {
    private final JdbcTemplate db;

    public PostgresScanJobStore(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    @Transactional
    public ScanJob createOrReuse(String requestKey, String owner, String repository,
            String oldCommit, String newCommit) {
        for (int attempt = 0; attempt < 3; attempt++) {
            ScanJob reusable = reusable(requestKey, owner);
            if (reusable != null) return reusable;
            UUID jobId = UUID.randomUUID();
            int inserted = db.update("""
                    INSERT INTO scan_jobs(job_id,request_key,owner_username,repository,
                        old_commit,new_commit,state,stage,progress)
                    VALUES (?,?,?,?,?,?,'QUEUED','QUEUED',0)
                    ON CONFLICT (owner_username,request_key)
                        WHERE state IN ('QUEUED','RUNNING','COMPLETED') DO NOTHING
                    """, jobId, requestKey, owner, repository, oldCommit, newCommit);
            if (inserted == 1) return get(jobId);
            // Re-read under READ COMMITTED after a competing INSERT commits. If
            // that job already became terminal, the next INSERT can create a job.
        }
        ScanJob reusable = reusable(requestKey, owner);
        if (reusable != null) return reusable;
        throw new PipelineException("SCAN_JOB_CONTENTION", 409,
                "Scan submissions changed concurrently; retry the request");
    }

    private ScanJob reusable(String requestKey, String owner) {
        List<ScanJob> values = db.query("""
                SELECT * FROM scan_jobs WHERE owner_username=? AND request_key=?
                    AND state IN ('QUEUED','RUNNING','COMPLETED')
                ORDER BY updated_at DESC LIMIT 1
                """, this::map, owner, requestKey);
        return values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public ScanJob get(UUID jobId) {
        List<ScanJob> values = db.query("SELECT * FROM scan_jobs WHERE job_id=?",
                this::map, jobId);
        return values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public boolean updateIfActive(UUID jobId, State state, String stage, int progress,
            String reasonCode, String scanId) {
        return db.update("""
                UPDATE scan_jobs SET state=?,stage=?,progress=?,reason_code=?,scan_external_id=?,
                    updated_at=now() WHERE job_id=? AND state<>'CANCELLED'
                """, state.name(), stage, progress, reasonCode, scanId, jobId) == 1;
    }

    @Override
    public ScanJob cancel(UUID jobId) {
        db.update("""
                UPDATE scan_jobs SET state='CANCELLED',stage='CANCELLED',
                    reason_code='CANCELLED_BY_USER',updated_at=now()
                WHERE job_id=? AND state IN ('QUEUED','RUNNING')
                """, jobId);
        return get(jobId);
    }

    @Override
    public List<ScanJob> listVisible(String username, boolean administrator, Instant before,
            UUID beforeId, int limit) {
        Instant anchor = before == null ? Instant.now().plusSeconds(1) : before;
        UUID anchorId = beforeId == null
                ? UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff") : beforeId;
        return db.query("""
                SELECT j.* FROM scan_jobs j WHERE
                    (? OR j.owner_username=? OR EXISTS (
                        SELECT 1 FROM scans s JOIN scan_memberships m ON m.scan_id=s.id
                        JOIN users u ON u.id=m.user_id
                        WHERE s.external_id=j.scan_external_id AND u.username=? AND u.enabled))
                    AND (j.updated_at < ? OR (j.updated_at = ? AND j.job_id < ?))
                ORDER BY j.updated_at DESC,j.job_id DESC LIMIT ?
                """, this::map, administrator, username, username, Timestamp.from(anchor),
                Timestamp.from(anchor), anchorId, limit);
    }

    @Override
    public int failInterruptedJobs() {
        return db.update("""
                UPDATE scan_jobs SET state='FAILED',stage='RECOVERY',
                    reason_code='PLATFORM_RESTARTED',updated_at=now()
                WHERE state IN ('QUEUED','RUNNING')
                """);
    }

    private ScanJob map(java.sql.ResultSet values, int row) throws java.sql.SQLException {
        Timestamp created = values.getTimestamp("created_at");
        Timestamp updated = values.getTimestamp("updated_at");
        return new ScanJob(values.getObject("job_id", UUID.class), values.getString("request_key"),
                values.getString("owner_username"), values.getString("repository"),
                values.getString("old_commit"), values.getString("new_commit"),
                State.valueOf(values.getString("state")), values.getString("stage"),
                values.getInt("progress"), values.getString("reason_code"),
                values.getString("scan_external_id"), created.toInstant(), updated.toInstant());
    }
}
