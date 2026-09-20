-- Needed to actually deliver EMAIL-channel notifications. Nullable: existing
-- accounts have none on record yet, and GitHub OAuth may not grant the email
-- scope. Populating it (profile form, OAuth email scope) is a follow-up.
ALTER TABLE users ADD COLUMN email VARCHAR(255);
