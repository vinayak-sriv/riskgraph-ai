CREATE TABLE notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    scan_id BIGINT NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    dedup_key VARCHAR(160) NOT NULL,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('REVIEW', 'BLOCK')),
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts SMALLINT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One outbox row per (scan, verdict): a re-save with an unchanged verdict is a
-- no-op insert; a verdict change (e.g. REVIEW -> BLOCK on validation) is a new event.
CREATE UNIQUE INDEX uq_notification_outbox_dedup_key ON notification_outbox(dedup_key);

-- The dispatcher's poll query: oldest-first, only what's due and not yet delivered.
CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox(next_attempt_at, id)
    WHERE status = 'PENDING';
