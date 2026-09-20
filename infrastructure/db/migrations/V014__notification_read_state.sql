-- Read state for in-app notifications. `status` on notification_deliveries
-- tracks send/retry outcome (PENDING/SENT/FAILED), not whether the recipient
-- has seen it, so it needs its own column.
ALTER TABLE notification_deliveries ADD COLUMN read_at TIMESTAMPTZ;

CREATE INDEX idx_notification_deliveries_unread
    ON notification_deliveries(user_id, id)
    WHERE read_at IS NULL;
