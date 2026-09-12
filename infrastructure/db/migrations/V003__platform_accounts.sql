-- Existing profile-only users stay disabled until explicitly provisioned.
ALTER TABLE users ADD COLUMN username VARCHAR(64) UNIQUE;
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD CONSTRAINT users_login_fields CHECK
    (NOT enabled OR (username IS NOT NULL AND password_hash IS NOT NULL));
