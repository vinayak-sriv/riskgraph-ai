# Deterministic analysis hardening report

Date: 2026-09-12

## Outcome

All requested P0 defects are covered by passing regression tests. Deterministic code remains the only owner of graph facts, risk, and ALLOW / REVIEW / BLOCK. Low-confidence or unresolved extraction evidence is excluded from confirmed graph input and forces degraded/review behavior.

## Functional changes

- Risk sensitivity is calculated only from route/resource pairs on newly anonymous paths. Unrelated authenticated resources cannot alter a finding score. Reachability is computed once for each revision and reused.
- Spring mapping values now have `RESOLVED`, `EXPLICITLY_EMPTY`, and `UNRESOLVED` states. Unresolved expressions produce diagnostics and never become root routes.
- Java call resolution uses Spoon declaring types, qualified types, signatures, argument types, boxing, and assignability. Ambiguous overloads stop confirmed traversal.
- Every resolved dependency path is retained and expanded into canonical graph input.
- Each new path receives a stable `finding_id`; evidence, AI interpretation, and validation are stored on that finding only.
- `SecurityFilterChain` presence and changes are detected, but they deliberately do not create authorization facts. Routes without annotation evidence remain unauthenticated with LOW authorization confidence and diagnostics.
- Service-wide scan serialization was replaced with per-scan locking. Graph nodes and edges use JDBC batch writes.
- Release archives use sorted entries, fixed metadata, and explicit exclusions. Python generators force LF output. Python dependencies use exact versions, Docker base images use versioned tags where available, and GitHub Actions use immutable commit pins.

## Contract and database changes

- Source-analysis envelope version: `1.0.0` to `1.1.0`.
- `dependency_paths[]` now requires `sensitivity`.
- Source scan results now require `findings[]`; each entry contains `finding_id`, route/resource/path evidence, and finding-scoped `ai` and `validation` values.
- `GET /scans/{scanId}` now returns `finding_id` and populated finding summaries. Existing top-level AI/validation fields remain for compatibility.
- Migration `V005__finding_identity_and_enrichment.sql` adds `findings.external_id`, enforces one AI and validation row per finding, and retains the newest pre-migration enrichment row when duplicates exist.

## Verification

- Java analyzer: 33 passed, 0 failed.
- Java platform API: 26 passed, 0 failed.
- Python: 65 passed, 0 failed; 96% coverage across graph-risk and AI-validation service modules.
- Frontend: 18 passed, 0 failed; TypeScript and Vite production build passed.
- Contract examples, OpenAPI YAML, and three Docker Compose profiles passed validation.
- Provisional 100-record corpus validation and oracle comparison passed with exact graph-path and verdict matches.
- Two complete release archives were byte-identical. The archive contained 554 source entries and zero forbidden artifacts.
- `git diff --check` reported no whitespace errors. Existing CRLF normalization notices remain on dashboard files that this task did not redesign.

During verification, the updated risk behavior exposed an old snapshot-wide corpus oracle and the platform security test exposed a live port dependency. Both were corrected; the final suites have zero failures.

## Changed files

Core analysis and persistence:

- `services/graph-risk-service/app/risk_engine.py`
- `services/java-analyzer/src/main/java/ai/riskgraph/analyzer/extract/SpringEndpointExtractor.java`
- `services/java-analyzer/src/main/java/ai/riskgraph/analyzer/extract/SpringMappingResolver.java`
- `services/java-analyzer/src/main/java/ai/riskgraph/analyzer/extract/ExecutableResolver.java`
- `services/java-analyzer/src/main/java/ai/riskgraph/analyzer/extract/AuthorizationResolver.java`
- `services/java-analyzer/src/main/java/ai/riskgraph/analyzer/model/AnalysisModels.java`
- `services/java-analyzer/src/main/java/ai/riskgraph/analyzer/service/AnalysisService.java`
- `services/platform-api/src/main/java/ai/riskgraph/platform/api/ScanController.java`
- `services/platform-api/src/main/java/ai/riskgraph/platform/service/SourceScanService.java`
- `services/platform-api/src/main/java/ai/riskgraph/platform/service/PostgresScanStore.java`
- `infrastructure/db/migrations/V005__finding_identity_and_enrichment.sql`

Tests:

- `services/java-analyzer/src/test/java/ai/riskgraph/analyzer/extract/SpringEndpointExtractorTest.java`
- `services/java-analyzer/src/test/java/ai/riskgraph/analyzer/service/AnalysisServiceIntegrationTest.java`
- `services/platform-api/src/test/java/ai/riskgraph/platform/security/PlatformSecurityTest.java`
- `services/platform-api/src/test/java/ai/riskgraph/platform/service/SourceScanServiceTest.java`
- `services/platform-api/src/test/java/ai/riskgraph/platform/service/SourceValidationTest.java`
- `tests/graph_risk/test_authorization_removal.py`
- `tests/graph_risk/test_hardening.py`
- `tests/release/test_package.py`
- `tests/release/test_text_generation.py`

Contracts, generated corpus, and documentation:

- `contracts/ir/analysis-envelope.schema.json`
- `contracts/ir/examples/analysis-envelope.json`
- `contracts/ir/graph-analysis.schema.json`
- `contracts/ir/risk-policy.schema.json`
- `contracts/ir/risk-result.schema.json`
- `contracts/ir/scan-result.schema.json`
- `contracts/ir/demo-result.schema.json`
- `contracts/ai/explanation.schema.json`
- `contracts/validation/sandbox-request.schema.json`
- `contracts/validation/sandbox-result.schema.json`
- `contracts/api/graph-risk-api.openapi.yaml`
- `contracts/api/ai-validation-api.openapi.yaml`
- `contracts/api/platform-api.openapi.yaml`
- `datasets/risk-corpus/tools/corpus.py`
- `datasets/risk-corpus/development.jsonl`
- `datasets/risk-corpus/calibration.jsonl`
- `datasets/risk-corpus/test.jsonl`
- `datasets/risk-corpus/dataset.schema.json`
- `datasets/risk-corpus/manifest.json`
- `docs/ir-contract.md`
- `docs/runtime-guide.md`
- [Development and release tools](../README.md#development-and-release-tools)
- `tools/dev/export_contracts.py`

Release and reproducibility:

- `.dockerignore`
- `.github/workflows/ci.yml`
- `.github/workflows/riskgraph-pr.yml`
- `requirements-dev.txt`
- `apps/dashboard/Dockerfile`
- `services/ai-validation-service/Dockerfile`
- `services/ai-validation-service/Dockerfile.validation`
- `services/ai-validation-service/requirements.txt`
- `services/graph-risk-service/Dockerfile`
- `services/graph-risk-service/requirements.txt`
- `services/java-analyzer/Dockerfile`
- `services/platform-api/Dockerfile`
- `infrastructure/docker-compose.yml`
- `tools/release/package.py`
- `tools/reporting/generate.py`
- `tools/reporting/submit_event.py`
- `tools/evaluation/external_spring.py`
- `tools/dev/build_sandboxes.py`
- `tools/dev/create_mvp_samples.py`
- `tools/dev/create_week6_sample.py`
- `tools/dev/init_auth.py`
- `tools/dev/start_local.py`
- `tools/dev/verify_access.py`
- `tools/dev/verify_mvp.py`
- `tools/dev/verify_ollama.py`
- `tools/dev/verify_release.py`
- `tools/dev/verify_runtime.py`
- `docs/deterministic-hardening-report.md`

## Deliberate limits

- Full `SecurityFilterChain` semantics remain Phase 2 under `AGENTS.md`. The current scaffold does not feed filter-chain rules into emitted IR; it only triggers fail-closed diagnostics and reduced confidence. Patterns, ordering conflicts, custom authorization managers, and custom DSLs therefore never become invented authorization facts.
- Source-bound automatic HTTP validation remains limited to the existing registered local Docker `/admin/export` fixture. Other finding routes receive an explicit `NOT_RUN / UNSUPPORTED_SOURCE_VALIDATION` result rather than borrowed validation.
- Graph node/edge persistence is batched. Finding enrichment remains per-finding upsert because each row has independent identity and state.
- The suggested extractor split was applied only where it improved testability: mapping, authorization, and executable resolution. The remaining extractor responsibilities were left in place to avoid an unrelated refactor.
