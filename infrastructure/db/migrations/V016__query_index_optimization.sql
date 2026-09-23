-- Remove indexes superseded by unique indexes added in later migrations.
-- Keeping both copies adds write amplification without improving lookup plans.
DROP INDEX IF EXISTS idx_scan_external_id;
DROP INDEX IF EXISTS idx_ai_analysis_finding_id;
DROP INDEX IF EXISTS idx_validation_tests_finding_id;

-- Persisting a graph immediately resolves stable node ids within one scan/revision.
-- The leading scan_id still supports foreign-key cleanup and scan-only lookups.
DROP INDEX IF EXISTS idx_graph_nodes_scan_id;
CREATE INDEX idx_graph_nodes_scan_revision_stable
    ON graph_nodes(scan_id, revision, stable_id)
    INCLUDE (id);

-- Keyset pagination for the in-app feed only reads successfully sent rows.
CREATE INDEX idx_notification_deliveries_feed
    ON notification_deliveries(user_id, channel, id DESC)
    WHERE status = 'SENT';

-- Unread counts and bulk read updates share this narrower predicate.
DROP INDEX IF EXISTS idx_notification_deliveries_unread;
CREATE INDEX idx_notification_deliveries_unread
    ON notification_deliveries(user_id, channel)
    WHERE status = 'SENT' AND read_at IS NULL;

-- PostgreSQL does not auto-index referencing columns. These indexes keep scan/user
-- deletion cascades from scanning the complete notification tables.
CREATE INDEX idx_notification_outbox_scan_id
    ON notification_outbox(scan_id);
CREATE INDEX idx_notification_deliveries_user_id
    ON notification_deliveries(user_id);

-- Startup recovery only visits unfinished jobs.
-- Keep updated_at out of the key so progress writes remain eligible for HOT updates.
CREATE INDEX idx_scan_jobs_active
    ON scan_jobs(job_id)
    WHERE state IN ('QUEUED', 'RUNNING');
