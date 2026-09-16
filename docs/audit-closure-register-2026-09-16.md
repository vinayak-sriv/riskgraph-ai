# Consolidated adversarial audit closure register — 2026-09-16

**Fixed** means implemented and tested. **Contained / Phase 2** means the broader
feature is outside the annotation-only MVP and current behavior visibly lowers
confidence and forces REVIEW. **Preserved** denotes a positive audited invariant.
**Mitigated** denotes non-release-blocking decomposition that must continue before
another large feature or redesign.

## Logic

| ID | Status | Implementation/evidence | Verification or release condition |
|---|---|---|---|
| L-01 | Fixed | Pattern-aware exact/prefix/suffix/regex sensitivity policy with rule/version evidence | `SensitivityPolicyTest`; compound sensitive names cannot silently fall to low impact |
| L-02 | Fixed | Repository-backed unknown resources receive conservative sensitivity and remain attack-path targets | `test_sensitivity_fallback.py`; a public defaulted repository path is at least REVIEW |
| L-03 | Contained / Phase 2 | Protected-role changes emit `UNRESOLVED_AUTHORIZATION_DELTA`, LOW confidence, incomplete evidence, REVIEW | `test_authorization_delta_containment.py`; policy AST required for full closure |
| L-04 | Contained / Phase 2 | Authenticated-principal expansion is explicitly not claimed by the MVP | Same containment test and `phase-2-audit-scope-gate.md`; principal graphs required later |
| L-05 | Contained / Phase 2 | Partial resolver is labeled experimental/disconnected; active extraction emits filter-chain diagnostics | `SpringEndpointExtractorTest`; integration requires roadmap/contract gate |
| L-06 | Contained / Phase 2 | Method/path filter matchers are never invented as facts | Filter-chain diagnostics force REVIEW; ordered tuple resolver required later |
| L-07 | Contained / Phase 2 | Filter-chain role/authority/access expressions remain visibly unsupported | LOW authorization confidence plus REVIEW; policy AST required later |
| L-08 | Fixed | Stable route node IDs coexist with source-located `handler_refs` | Graph/API contract tests and result schemas |
| L-09 | Fixed | Every finding carries an authoritative route-specific `risk_result`; scan risk remains separate | `FindingEnrichmentServiceTest`, reporting contract tests |
| L-10 | Fixed | Zero findings return `NOT_RUN / NO_VALIDATABLE_FINDING` without invoking validation | `SourceValidationTest`, AI validation API tests |
| L-11 | Fixed | Per-finding `validation_capability` and status distinguish supported probes | `SourceValidationTest`, dashboard evidence tests |
| L-12 | Fixed | Aggregation preserves observed status collections and never synthesizes 403 | `test_validation.py`, reporting contract tests |
| L-13 | Fixed | Identity derives from canonical provenance, revisions, analyzer/config, and policy inputs | `ScanIdentityFactoryTest`, async idempotency tests |

## Security

| ID | Status | Implementation/evidence | Verification or release condition |
|---|---|---|---|
| S-01 | Fixed | Isolated Docker is required by default; host daemon is explicit local-only opt-in | `test_validation.py`, validation Compose overlay, runtime guide |
| S-02 | Fixed | Analyzer, graph, and AI services use distinct credentials and audiences | `InternalServiceAuthFilterTest`, `AnalysisClientAudienceTest`, Python auth tests |
| S-03 | Fixed | Delegation requires owner/admin sharing authority; VALIDATE alone cannot grant | `PlatformSecurityTest`, scan access tests |
| S-04 | Fixed | Public health omits Ollama URL/model infrastructure details | AI API tests and health contract |
| S-05 | Fixed | Deployed demo execution is bounded/rate-limited; fixtures remain input-limited | `DemoScenarioServiceTest`, deployment settings |
| S-06 | Fixed | Client IP trusts forwarding only in configured proxy mode | `ClientAddressResolverTest`, runtime docs |
| S-07 | Fixed | Structured LLM evidence is allowlisted and scrubbed before prompts | `test_reasoning.py`; raw source/secrets stay excluded |
| S-08 | Preserved | JGit materializes limited blobs; target repositories are never built/executed | `GitSourceAcquirerTest`, container contract tests |
| S-09 | Preserved | Fixed digest probe, internal network, read-only/non-root/capability/resource restrictions | Validation and container contract tests |
| S-10 | Preserved | CSRF, origin/CORS, sessions, passwords, OAuth/PKCE, and stale-session controls remain | `PlatformSecurityTest`, dashboard auth E2E |

## Performance

| ID | Status | Implementation/evidence | Verification or release condition |
|---|---|---|---|
| P-01 | Fixed | Normal/focused/summarized graph modes and explicit detail loading | Presentation-mode unit tests and keyboard graph E2E |
| P-02 | Fixed | Large revisions do not render two complete graphs by default | `useGraphPresentationMode.test.ts` |
| P-03 | Fixed | Change details are summarized and history is cursor-paginated | Dashboard tests and `AsyncScanJobServiceTest` |
| P-04 | Fixed | One bounded enrichment executor exposes queue/capacity and saturation degradation | `EnrichmentExecutorTest`, `FindingEnrichmentServiceTest` |
| P-05 | Fixed | Persisted job states, polling, cancellation, restart recovery | `AsyncScanJobServiceTest`, dashboard polling tests |
| P-06 | Fixed | Immutable full-SHA snapshots use locks, leases, completion markers, size/age/count bounds | `SnapshotCacheTest`, cache benchmark evidence |
| P-07 | Fixed | Deterministic records cache by analyzer/config identity; clean/cached results are equal | `AnalysisServiceIntegrationTest`, `DeterministicExtractionCacheTest` |
| P-08 | Fixed | Canonical expansion rejects the 2,001st row before retaining it | `CanonicalGraphInputBuilderTest` |
| P-09 | Fixed | CSRF caches per session and retries once only on genuine CSRF failure | `api.test.ts`, `App.test.tsx` |
| P-10 | Fixed | Worker/temp cleanup and cache eviction are logged and path-safe/bounded | `TempArtifactSweeperTest`, `SnapshotCacheTest` |

## Dashboard

| ID | Status | Implementation/evidence | Verification or release condition |
|---|---|---|---|
| D-01 | Fixed | Confirmation names the specific HTTP probe and source-bound Docker sandbox | Evidence tests and validation E2E |
| D-02 | Fixed | LOW/incomplete/degraded evidence replaces the normal hero with REVIEW REQUIRED | `Overview.test.tsx`, degraded E2E |
| D-03 | Fixed | Placeholder history became membership-filtered paginated Saved scans | `App.test.tsx`, scan job/history endpoints |
| D-04 | Fixed | Failed refresh labels retained evidence stale until successful replacement | Stale-result unit and E2E tests |
| D-05 | Fixed | Each finding shows independent capability/status/result | Evidence panel tests |
| D-06 | Fixed | Browser gate covers login/GitHub/logout, degradation, validation/stale, access denial | Four Playwright tests |
| D-07 | Fixed | Large-graph modes, worker layout, ceilings, focused rendering are automated | Graph tests and production build |
| D-08 | Preserved | Keyboard/focus behavior and serious axe checks | Playwright graph/auth accessibility flows |
| D-09 | Preserved | Source, demo, and offline fixture identity stay visibly distinct | App tests and provenance UI |

## Code quality

| ID | Status | Implementation/evidence | Verification or release condition |
|---|---|---|---|
| C-01 | Fixed | The 70 KB stylesheet is an ordered six-module entry point; the app analysis workspace and graph panels are isolated presentation components | Prettier, ESLint, production build, 42 unit tests, and four browser workflows |
| C-02 | Fixed | Authorization, dependency, changed-surface, and confidence logic are characterized components | 19 extractor tests plus real-commit integration |
| C-03 | Fixed | Canonical input, identity, job persistence/lifecycle, validation, and enrichment are separate services | Platform suite and boundaries |
| C-04 | Fixed | Backend thresholds, weights, and bands are authoritative contract data | Reporting and dashboard tests |
| C-05 | Contained / Phase 2 | Experimental resolver is labeled and disconnected | Same Batch-11 gate as L-05–L-07 |
| C-06 | Fixed | Python branch gate 93%; frontend statement/branch/function/line gates 72/67/72/74 | Python 94.77%; frontend 74.62/67.26/74.37/77.25 |
| C-07 | Fixed | Touched Java follows Checkstyle and conventional statement layout | Both Maven verify/checkstyle gates |
| C-08 | Fixed | API errors stay sanitized; local structured logs retain safe diagnostic context | Validation tests and troubleshooting docs |
| C-09 | Preserved | SHA-pinned least-privilege CI and security/package gates remain | Workflow review, npm audit, contract tests |

## Verification snapshot

Passed locally on 2026-09-16:

- Java analyzer: 53 tests; Maven verify/checkstyle/JAR/JaCoCo.
- Platform API: 61 tests; Maven verify/checkstyle/JAR.
- Python: 123 tests; branch-aware aggregate coverage 94.77% against 93%.
- Dashboard: lint, formatting, production build, 42 tests; coverage 74.62%
  statements, 67.26% branches, 74.37% functions, 77.25% lines.
- Browser: four Chromium Playwright workflows including serious axe checks.
- Contracts: export check and Compose configuration pass; Ruff and
  `git diff --check` pass; `npm audit --audit-level=high` reports zero findings.

The isolated Docker smoke was rerun on Docker 29.7.2. All services became healthy,
Flyway applied migrations V001–V009, and all four source-backed MVP scenarios passed.
Authorization removal completed a real sandbox probe with `CONFIRMED` status. A
pinned Trivy 0.74.0 scan reported zero fixed CRITICAL vulnerabilities across all 12
runtime, infrastructure, probe-base, and sandbox images. See
`docs/evidence/docker-release-gate-2026-09-16.md` for the reproducible evidence.
