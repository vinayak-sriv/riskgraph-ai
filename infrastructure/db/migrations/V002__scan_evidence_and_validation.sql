ALTER TABLE scans ADD COLUMN external_id VARCHAR(64) UNIQUE;
ALTER TABLE scans ADD COLUMN result_json JSONB;
ALTER TABLE scans ADD COLUMN pre_validation_verdict VARCHAR(16);
ALTER TABLE scans ADD COLUMN final_verdict VARCHAR(16);
ALTER TABLE scans ADD COLUMN validation_status VARCHAR(32) NOT NULL DEFAULT 'NOT_RUN';
ALTER TABLE graph_nodes ADD COLUMN revision VARCHAR(8) NOT NULL DEFAULT 'after';
ALTER TABLE graph_nodes ADD COLUMN stable_id TEXT;
ALTER TABLE graph_nodes ALTER COLUMN name TYPE TEXT;
ALTER TABLE projects ALTER COLUMN repository TYPE TEXT;
ALTER TABLE validation_tests DROP CONSTRAINT validation_tests_status_check;
ALTER TABLE validation_tests ADD CONSTRAINT validation_tests_status_check CHECK
  (status IN ('PENDING','PASSED','FAILED','ERROR','SKIPPED','CONFIRMED','REJECTED','INCONCLUSIVE','NOT_RUN'));
CREATE INDEX idx_projects_repository ON projects(repository);
CREATE INDEX idx_scan_external_id ON scans(external_id);
