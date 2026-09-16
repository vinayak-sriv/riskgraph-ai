ALTER TABLE scan_memberships
    DROP CONSTRAINT IF EXISTS scan_memberships_access_level_check;

ALTER TABLE scan_memberships
    ADD CONSTRAINT scan_memberships_access_level_check
    CHECK (access_level IN ('OWNER', 'VALIDATE', 'VIEW'));

-- Existing VALIDATE grants are deliberately not promoted. Only a new scan claim
-- creates OWNER authority; administrators retain their platform-level override.
