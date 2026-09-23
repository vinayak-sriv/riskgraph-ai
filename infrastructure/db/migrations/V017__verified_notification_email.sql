-- Email delivery is opt-in and requires an administrator-confirmed address.
-- Existing addresses stay unverified until explicitly reviewed.
ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_users_verified_email
    ON users(id)
    WHERE enabled AND email_verified AND email IS NOT NULL;
