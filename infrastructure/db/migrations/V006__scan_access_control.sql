CREATE TABLE scan_memberships (
    scan_id BIGINT NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_level VARCHAR(16) NOT NULL CHECK (access_level IN ('VIEW', 'VALIDATE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (scan_id, user_id)
);

CREATE INDEX idx_scan_memberships_user_id ON scan_memberships(user_id);

-- Existing rows are intentionally not shared automatically. An administrator must
-- explicitly grant access, which avoids silently exposing pre-migration scans.
