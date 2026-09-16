CREATE TABLE scan_jobs (
    job_id UUID PRIMARY KEY,
    request_key VARCHAR(64) NOT NULL,
    owner_username VARCHAR(64) NOT NULL REFERENCES users(username),
    repository TEXT NOT NULL,
    old_commit VARCHAR(40) NOT NULL,
    new_commit VARCHAR(40) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK
        (state IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    stage VARCHAR(32) NOT NULL,
    progress SMALLINT NOT NULL CHECK (progress BETWEEN 0 AND 100),
    reason_code VARCHAR(64),
    scan_external_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_scan_jobs_reusable_request
    ON scan_jobs(owner_username, request_key)
    WHERE state IN ('QUEUED', 'RUNNING', 'COMPLETED');
CREATE INDEX idx_scan_jobs_owner_updated
    ON scan_jobs(owner_username, updated_at DESC, job_id DESC);
CREATE INDEX idx_scan_jobs_scan_external_id ON scan_jobs(scan_external_id);
