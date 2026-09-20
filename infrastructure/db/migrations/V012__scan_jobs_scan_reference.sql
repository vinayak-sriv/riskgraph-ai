-- scan_jobs.scan_external_id pointed at scans.external_id with no constraint,
-- so a project cascade delete left COMPLETED jobs whose result 404s forever.
-- Clear any rows that are already dangling, then enforce the reference.
UPDATE scan_jobs
SET scan_external_id = NULL
WHERE scan_external_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM scans WHERE scans.external_id = scan_jobs.scan_external_id
  );

ALTER TABLE scan_jobs
    ADD CONSTRAINT fk_scan_jobs_scan
    FOREIGN KEY (scan_external_id) REFERENCES scans (external_id)
    ON DELETE SET NULL;
