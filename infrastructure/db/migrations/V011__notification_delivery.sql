CREATE TABLE notification_preferences (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL')),
    min_severity VARCHAR(16) NOT NULL DEFAULT 'REVIEW' CHECK (min_severity IN ('REVIEW', 'BLOCK')),
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, channel)
);
-- No row for a (user, channel) = the default: IN_APP on, EMAIL off, threshold REVIEW.

CREATE TABLE notification_unsubscribes (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- '*' = unsubscribed from everything; otherwise a repository identity.
    scope VARCHAR(255) NOT NULL DEFAULT '*',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, scope)
);

CREATE TABLE notification_deliveries (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES notification_outbox(event_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL')),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts SMALLINT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    failed_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One delivery row per (event, recipient, channel): resolving recipients twice
-- for the same event is a no-op insert, not a duplicate send.
CREATE UNIQUE INDEX uq_notification_deliveries_recipient
    ON notification_deliveries(event_id, user_id, channel);

CREATE INDEX idx_notification_deliveries_pending
    ON notification_deliveries(next_attempt_at, id)
    WHERE status = 'PENDING';
