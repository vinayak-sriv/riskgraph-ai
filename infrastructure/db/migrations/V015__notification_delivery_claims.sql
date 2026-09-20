-- Delivery workers claim rows in a short transaction, then perform network I/O after commit.
ALTER TABLE notification_deliveries
    DROP CONSTRAINT notification_deliveries_status_check;
ALTER TABLE notification_deliveries
    ADD CONSTRAINT notification_deliveries_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED'));

DROP INDEX idx_notification_deliveries_pending;
CREATE INDEX idx_notification_deliveries_claimable
    ON notification_deliveries(next_attempt_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');
