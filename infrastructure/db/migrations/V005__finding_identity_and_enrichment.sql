ALTER TABLE findings ADD COLUMN external_id VARCHAR(64);

CREATE UNIQUE INDEX uq_findings_external_id ON findings(external_id) WHERE external_id IS NOT NULL;

-- Pre-V005 saves could append enrichment rows. Retain the newest row before
-- enforcing the one-enrichment-per-finding contract used by the upserts.
DELETE FROM ai_analysis older
USING ai_analysis newer
WHERE older.finding_id = newer.finding_id AND older.id < newer.id;

DELETE FROM validation_tests older
USING validation_tests newer
WHERE older.finding_id = newer.finding_id AND older.id < newer.id;

CREATE UNIQUE INDEX uq_ai_analysis_finding_id ON ai_analysis(finding_id);
CREATE UNIQUE INDEX uq_validation_tests_finding_id ON validation_tests(finding_id);
