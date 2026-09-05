CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL CHECK (role IN ('Developer', 'Security Analyst', 'Admin')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    repository VARCHAR(512) NOT NULL,
    framework VARCHAR(128) NOT NULL CHECK (framework = 'Spring Boot'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pull_requests (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    old_commit VARCHAR(64) NOT NULL,
    new_commit VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scans (
    id BIGSERIAL PRIMARY KEY,
    pull_request_id BIGINT NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'ALLOW', 'REVIEW', 'BLOCK', 'FAILED')),
    risk_before INTEGER NOT NULL DEFAULT 0 CHECK (risk_before BETWEEN 0 AND 100),
    risk_after INTEGER NOT NULL DEFAULT 0 CHECK (risk_after BETWEEN 0 AND 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE graph_nodes (
    id BIGSERIAL PRIMARY KEY,
    scan_id BIGINT NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    node_type VARCHAR(64) NOT NULL CHECK (
        node_type IN (
            'USER',
            'ROLE',
            'ENDPOINT',
            'CONTROLLER',
            'FUNCTION',
            'SERVICE',
            'DATABASE',
            'DATA_RESOURCE',
            'EXTERNAL_SERVICE'
        )
    ),
    name VARCHAR(512) NOT NULL
);

CREATE TABLE graph_edges (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES graph_nodes(id) ON DELETE CASCADE,
    target_id BIGINT NOT NULL REFERENCES graph_nodes(id) ON DELETE CASCADE,
    relationship VARCHAR(64) NOT NULL CHECK (
        relationship IN (
            'CAN_ACCESS',
            'CALLS',
            'READS',
            'WRITES',
            'REQUIRES_ROLE',
            'CONNECTS_TO',
            'RETURNS',
            'HAS_ROLE'
        )
    )
);

CREATE TABLE findings (
    id BIGSERIAL PRIMARY KEY,
    scan_id BIGINT NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    type VARCHAR(128) NOT NULL,
    severity VARCHAR(32) NOT NULL CHECK (severity IN ('LOW', 'MODERATE', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_analysis (
    id BIGSERIAL PRIMARY KEY,
    finding_id BIGINT NOT NULL REFERENCES findings(id) ON DELETE CASCADE,
    hypothesis TEXT NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE validation_tests (
    id BIGSERIAL PRIMARY KEY,
    finding_id BIGINT NOT NULL REFERENCES findings(id) ON DELETE CASCADE,
    test TEXT NOT NULL,
    expected_result TEXT NOT NULL,
    actual_result TEXT,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PASSED', 'FAILED', 'ERROR', 'SKIPPED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pull_requests_project_id ON pull_requests(project_id);
CREATE INDEX idx_scans_pull_request_id ON scans(pull_request_id);
CREATE INDEX idx_graph_nodes_scan_id ON graph_nodes(scan_id);
CREATE INDEX idx_graph_edges_source_id ON graph_edges(source_id);
CREATE INDEX idx_graph_edges_target_id ON graph_edges(target_id);
CREATE INDEX idx_findings_scan_id ON findings(scan_id);
CREATE INDEX idx_ai_analysis_finding_id ON ai_analysis(finding_id);
CREATE INDEX idx_validation_tests_finding_id ON validation_tests(finding_id);
