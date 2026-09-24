-- Needed to actually deliver EMAIL-channel notifications. Nullable: existing
-- accounts have none on record yet, and GitHub OAuth may not grant the email
-- scope. V017 adds explicit administrator verification before delivery.
ALTER TABLE users ADD COLUMN email VARCHAR(255);
