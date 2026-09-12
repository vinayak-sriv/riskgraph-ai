-- GitHub establishes identity; RiskGraph continues to own authorization roles.
ALTER TABLE users DROP CONSTRAINT users_login_fields;
ALTER TABLE users ADD CONSTRAINT users_enabled_username CHECK
    (NOT enabled OR username IS NOT NULL);

CREATE TABLE external_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL CHECK (provider IN ('github')),
    provider_subject VARCHAR(128) NOT NULL,
    provider_login VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_subject),
    UNIQUE (user_id, provider)
);

CREATE INDEX idx_external_identities_user_id ON external_identities(user_id);
