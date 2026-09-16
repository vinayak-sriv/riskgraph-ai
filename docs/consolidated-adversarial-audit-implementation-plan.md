# RiskGraph AI consolidated adversarial audit implementation plan

Date: 2026-09-15
Planning baseline: current working tree plus the consolidated audit supplied on 2026-09-15
Current roadmap gate: Week 12 independent review pending

## Implementation status — 2026-09-16

| Batch | Status | Evidence |
|---:|---|---|
| 0–6 | Complete | Audit containment, sensitivity, evidence fidelity, graph ceilings, deployment controls, identity, queues, cleanup, and CSRF regressions |
| 7 | Complete with continuing decomposition debt | Extractor responsibilities, scan canonicalization, graph worker/hooks, coverage gates, Playwright, and axe are implemented; global CSS remains a deliberate follow-up before another redesign |
| 8 | Complete | Persisted asynchronous scan jobs, polling, cancellation, recovery, access-filtered cursor history, and real Saved scans UI |
| 9 | Complete | Bounded immutable snapshot/extraction caches, equivalence and eviction tests, and measured cache evidence |
| 10–11 | Scope-gated | Phase-2 semantics are deferred by `AGENTS.md`; unsupported deltas fail closed to REVIEW as documented in `docs/phase-2-audit-scope-gate.md` |
| 12 | Implementation complete; independent review remains | Unit/contract/coverage/build/E2E/axe gates, isolated Docker smoke, real sandbox confirmation, and the 12-image CRITICAL vulnerability gate pass; Week 12 external labels and human approval remain roadmap release gates |

## Outcome

Execute the audit correctness-first. The first release gate is to eliminate or safely contain false `ALLOW` outcomes, make validation claims exact, and prevent valid large analyses from freezing the dashboard. Performance, maintainability, and product-completeness work follows only after those gates pass.

This plan covers every audit ID. Positive findings are treated as invariants with regression tests, not as code to rewrite.

## Scope reconciliation

Five logic findings conflict with the current MVP boundary in `AGENTS.md`:

- L-03 and L-04 require privilege/role expansion modeling, currently designated Phase 2.
- L-05, L-06, and L-07 require `SecurityFilterChain` parsing, explicitly outside the annotation-only MVP.

They are handled in two stages:

1. **MVP containment:** recognize these changes as unsupported or semantically incomplete, lower confidence, and deterministically force `REVIEW`; never emit a high-confidence `ALLOW`.
2. **Phase-2 resolution:** implement the authorization-policy model and filter-chain resolver only after the roadmap checkpoint is approved and `AGENTS.md` is updated.

The sensitivity fix remains within MVP. To avoid an immediate breaking `UNKNOWN` enum addition, the first implementation should conservatively emit `MEDIUM` for unmatched repository-backed resources, record `SENSITIVITY_POLICY_DEFAULTED`, and force at least `REVIEW` when such a resource becomes public. A future versioned contract may add `UNKNOWN` if the team wants it as a first-class sensitivity value.

## Non-negotiable invariants

- Graph edges, authorization deltas, risk scores, and verdicts remain deterministic.
- The LLM receives structured evidence only and never decides risk or verdict.
- Target repositories are parsed, never built or executed.
- Runtime probes run only against registered local Docker sandboxes.
- Full immutable Git SHAs, provenance, coverage, diagnostics, and policy/config versions remain attached to evidence.
- Risk and confidence remain separate concepts.
- Existing CSRF, credentialed CORS, BCrypt, PKCE, route authorization, Docker probe restrictions, source limits, pinned CI actions, and stale-request/logout protection must not be weakened.

## Batch summary and order

| Batch | Gate | Primary result | Audit coverage |
|---|---|---|---|
| 0 | Baseline | Adversarial regression harness and explicit unsupported-case containment | L-03–L-07 containment, C-06, S-08–S-10, C-09, D-08–D-09 |
| 1 | P0 correctness | Conservative, pattern-aware sensitivity and no unknown-resource false `ALLOW` | L-01, L-02 |
| 2 | P0 evidence integrity | Exact validation semantics and confidence-led dashboard language | L-10–L-12, D-01–D-05 |
| 3 | P0 demo safety | Large-graph display guard, clustering, virtualization, and worker layout | P-01–P-03, D-07, part of C-01 |
| 4 | Deployment security | Fail-closed Docker validation, narrower trust, correct sharing, and safe diagnostics | S-01–S-07, C-08; preserve S-08–S-10 |
| 5 | Result correctness | Stable route/handler identity, per-finding risk, canonical scan identity, policy metadata | L-08, L-09, L-13, C-04 |
| 6 | Bounded resource use | Shared enrichment queue, early limits, CSRF reuse, and temp hygiene | P-04, P-08–P-10 |
| 7 | Change safety | Break up gravity wells and add browser/accessibility/coverage gates | C-01–C-07, D-06, D-08 |
| 8 | Multi-user readiness | Asynchronous cancellable scan jobs and real paginated scan history | P-05, D-03 final implementation |
| 9 | Analyzer scalability | Immutable snapshot and extraction/model caching with equivalence tests | P-06, P-07 |
| 10 | Phase-2 gate | Deterministic authorization policy/principal delta model | L-03, L-04 |
| 11 | Phase-2 gate | Ordered, method-aware `SecurityFilterChain` resolution | L-05–L-07, C-05 |
| 12 | Release gate | Full audit closure, corpus evaluation, performance report, and evidence bundle | All IDs and preserved positives |

Do not run Batches 10–11 as full implementations until the scope gate is explicitly approved. Batches 0–9 can proceed under the current project rules.

## Batch 0 — Freeze the baseline and prevent unsupported false confidence

### Changes

1. Add an audit traceability test manifest to `datasets/risk-corpus` mapping each adversarial fixture to its expected verdict, confidence, diagnostic, and supported/unsupported status.
2. Add cases for compound resource names, unknown resources, misleading repository names, role downgrade, authority replacement, multiple dependency paths, renamed resources, unconventional mappings, nested services, and ambiguous dispatch.
3. Add Phase-2 sentinel cases for method-specific and overlapping filter-chain matchers plus `anyRequest()` ordering. Until Batch 11, their expected result is degraded evidence and `REVIEW`, not a claimed resolved finding.
4. Add a deterministic containment rule for annotation role/authority changes that the current boolean model cannot compare. Emit a structured diagnostic such as `UNRESOLVED_AUTHORIZATION_DELTA`, set analysis incomplete, and force `REVIEW`.
5. Capture current precision, recall, F1, false-positive/false-negative set, scan time, graph size, dashboard render behavior, and coverage as the before baseline.
6. Convert positive audit findings into regression assertions: target source is never executed; Docker probe arguments remain fixed and restricted; web security remains fail-closed; CI actions remain pinned; source/demo/offline identity remains visible.
7. Verify and close S-04: the current working tree already limits AI health output to `status` and `service`; add a response-schema test and ensure the OpenAPI contract matches.

### Acceptance gate

- Every adversarial case has an explicit expected outcome.
- Unsupported authorization semantics cannot produce high-confidence `ALLOW`.
- Existing four MVP demo scenarios remain reproducible.
- Baseline metrics and commands are committed to the evidence bundle.

### Suggested commits

1. `test(audit): add adversarial false-allow corpus`
2. `fix(policy): force review for unresolved authorization deltas`
3. `test(security): lock preserved security invariants`

## Batch 1 — Fix sensitivity and attack-path false negatives

### Changes

1. Replace the flat exact-name policy with versioned structured rules supporting `exact`, `prefix`, `suffix`, and bounded/validated `regex` matching.
2. Define deterministic precedence: exact first, then longest literal prefix/suffix, then declared regex order, then fallback. Reject duplicate/ambiguous rules at startup.
3. Add compound-name rules for the audited examples: `CustomerProfile`, `CustomerExport`, `PaymentTransaction`, `PaymentMethod`, `AccessToken`, `UserCredential`, and `SecretConfig`.
4. Change the repository-backed default from quiet `MODERATE` to conservative `MEDIUM`. Preserve explicit `LOW`/`MODERATE` classifications when a rule intentionally assigns them.
5. Emit sensitivity rule ID, policy version/fingerprint, match kind, and `SENSITIVITY_POLICY_DEFAULTED` diagnostics in source evidence. Defaulted classification lowers confidence but does not lower impact.
6. Ensure a newly anonymous path to a defaulted repository-backed resource is a graph target and deterministically yields at least `REVIEW`.
7. Include every matching semantic field in the sensitivity-policy fingerprint so rule edits invalidate cached identities.
8. Update the bundled YAML, Java parser/model, schemas/examples where evidence metadata changes, Python fixtures, documentation, and policy tests together.

### Acceptance gate

- All audited compound names classify at the intended HIGH/CRITICAL level.
- An unrecognized repository-backed resource newly reachable by anonymous users cannot receive `ALLOW`.
- Explicit safe classifications still work and do not create blanket false positives.
- Rule ordering and fingerprints are stable across runs.
- Corpus precision/recall is reported before and after; no unexplained regression is accepted.

### Primary files

- `services/java-analyzer/.../extract/SensitivityPolicy.java`
- `services/java-analyzer/src/main/resources/sensitivity-policy.yml`
- `services/java-analyzer/.../extract/SpringEndpointExtractor.java`
- `services/graph-risk-service/app/graph_engine.py`
- `services/graph-risk-service/app/risk_engine.py`
- `contracts/ir/*`
- `datasets/risk-corpus/*`

## Batch 2 — Make validation and decision presentation exact

### Changes

1. If a scan has no deterministic findings, do not call the validator. Return `NOT_RUN` with `NO_VALIDATABLE_FINDING`.
2. Add per-finding `validation_capability` (`SUPPORTED` or `UNSUPPORTED`) and keep it distinct from execution `status` (`CONFIRMED`, `REJECTED`, `NOT_RUN`, `INCONCLUSIVE`, `ERROR`).
3. Select validation only from findings whose method/path and registered source-bound sandbox match the fixed probe. Unsupported findings remain explicitly unsupported.
4. Remove synthesized aggregate HTTP statuses. Aggregate observed status codes into a set/list and preserve individual validation results.
5. Make scan-level confirmation wording identify the exact confirmed method, path, source commit, sandbox revision, and immutable images. Never imply that one confirmed finding confirms every finding.
6. Render a validation status/capability badge beside every finding.
7. When confidence is LOW, coverage incomplete, or analysis status DEGRADED, replace the normal decision hero with `REVIEW REQUIRED — incomplete evidence`, while still showing the deterministic preliminary verdict as secondary detail.
8. Add a persistent `STALE RESULT` banner after failed refreshes, containing the displayed repository identity and old/new commits. Clear it only after a successful replacement result or logout.
9. Immediately rename the unfinished `Saved scans` destination to `Open scan by ID` and remove `Listing API pending`. Batch 8 provides the optional full history experience.
10. Version and update validation JSON Schema, OpenAPI, Java records, TypeScript types, examples, and tests in one contract-first change.

### Acceptance gate

- A zero-finding scan causes zero validation-service requests.
- A 401 remains 401 and a 403 remains 403; aggregate data never invents either.
- Mixed supported/unsupported findings display independent statuses.
- Low-confidence/degraded evidence cannot be presented with a normal authoritative `ALLOW` or `BLOCK` hero.
- A failed refresh leaves old evidence visibly and unavoidably marked stale.
- Wording is scenario-specific and passes dashboard copy assertions.

## Batch 3 — Prevent graph-size dashboard failure

### Changes

1. Calculate graph complexity before mounting ReactFlow or Dagre.
2. Enforce three default presentation modes:
   - at most 100 visual nodes: normal graph and compare mode;
   - 101–500 visual nodes: changed/path-focused data and one revision at a time;
   - over 500 visual nodes: summarized clusters, counts, and an explicit detailed-load action.
3. For large graphs, default to the highest-risk changed path and `after` revision. Do not simultaneously render both full revisions.
4. Move Dagre layout to a Web Worker. Support request cancellation and ignore stale worker results.
5. Cluster unchanged components by node type/package or collapsed dependency chain without changing deterministic graph data.
6. Replace one-chip-per-change rendering with totals by node/edge type. Put details behind an expandable virtualized list.
7. Virtualize or paginate the node directory and cap accessible detail pages without silently dropping counts.
8. Split graph derivation/state from rendering as the first C-01 refactor: `useGraphDiff`, `useGraphPresentationMode`, worker adapter, summary, and panels.
9. Add generated 100/500/1,000/2,000-row fixtures and browser responsiveness tests. Record layout time, first usable render, long tasks, and interaction latency.

### Acceptance gate

- A maximum-size valid backend result opens in summary mode without blocking the main thread or mounting thousands of ReactFlow elements.
- Compare mode cannot render two oversized full graphs by default.
- Changed-path evidence remains reachable by keyboard and screen reader in every mode.
- Counts always refer to the full deterministic graph, not only the rendered subset.
- Performance budgets are recorded from the CI runner and then enforced with a small documented tolerance.

## Batch 4 — Tighten deployment and service security

### Changes

1. Make the allowlisted isolated daemon (`tcp://validation-docker:2375`) the default requirement. Missing configuration means validation unavailable.
2. Permit host-daemon access only under an explicit local-development profile/flag, visibly warn at startup, expose no production fallback, and add negative configuration tests.
3. Replace the shared service token with audience-specific credentials for platform-to-analyzer, platform-to-graph, and platform-to-AI calls. Each service accepts only its own audience. Document rotation; reserve mTLS for a later deployment profile if needed.
4. Change scan membership to `OWNER`, `VALIDATE`, and `VIEW` (or add explicit `SHARE`). Claiming a scan creates ownership; only owner/admin/share capability can delegate. Add a database migration and authorization matrix tests.
5. In deployed profiles, serve demo results from precomputed fixtures. If dynamic demo analysis remains enabled locally, add a bounded rate limit and concurrency guard.
6. Document trusted reverse-proxy behavior. Use forwarded client IP only when the platform is configured behind an explicitly trusted proxy; otherwise continue using the direct peer address. Test both modes.
7. Put a structured allowlist/scrubber immediately before LLM requests. Remove unexpected credential-like fields and secret patterns, and fail closed if future evidence starts carrying raw source.
8. Preserve sanitized Docker/API error codes but add structured local logs with operation, run ID, daemon mode, exit category, and cleanup result. Never log tokens, response bodies, or raw secrets.
9. Re-run invariant tests for non-execution of target repositories, fixed Docker probe inputs/restrictions, and existing web security controls.

### Acceptance gate

- No default or production path can reach the host Docker socket/pipe.
- A credential intended for one service is rejected by every other service.
- A validator cannot share a scan unless independently granted share/owner authority.
- Public demo traffic cannot create unbounded internal analysis work.
- Operational logs explain failures without exposing sensitive values.

## Batch 5 — Stabilize identities and calculate risk per finding

### Changes

1. Keep route identity stable as `method + normalized path`, but attach a separate deterministic handler identity derived from controller/method signature and source provenance.
2. Detect multiple handlers for the same route. Preserve each handler reference and emit an ambiguity diagnostic instead of collapsing conflicting implementations silently.
3. Add a graph-risk result for each route/resource candidate containing Risk Before, Risk After, Risk Delta, categories, components, evidence, and policy version.
4. Aggregate scan-level risk from the documented deterministic policy (for example, highest post-change impact with deterministic tie-breakers); do not copy scan severity onto every finding.
5. Build finding severity/evidence from its matching per-finding result.
6. Replace `digest(result.toString())` with `ScanIdentityFactory` over canonical JSON containing repository identity, full old/new SHAs, schema/analyzer version, analyzer config hash, sensitivity-policy fingerprint, and risk-policy version. Exclude timestamps, AI output, validation output, ordering accidents, and rendered result text.
7. Return risk thresholds and policy metadata in the backend contract. Render dashboard bands from that metadata instead of hard-coded duplicated values.
8. Perform a versioned contract update across JSON Schema, OpenAPI, Java/Python models, examples, TypeScript types, persistence, SARIF, and GitHub reporting. Update `AGENTS.md` if the IR gains optional fields.

### Acceptance gate

- Two handlers sharing a route remain distinguishable in evidence.
- Two findings in one scan may correctly have different severities.
- Logically identical inputs yield the same scan ID regardless of JSON property/list construction order or AI/validation state.
- Any analyzer/config/policy/revision change that affects semantics changes the scan ID.
- Frontend risk bands exactly match returned policy metadata.

## Batch 6 — Bound concurrency, expansion, CSRF traffic, and temporary data

### Changes

1. Replace the per-scan fixed thread pool with one application-level bounded executor and queue. Configure workers/queue, reject or degrade predictably under saturation, and expose queue health without secrets.
2. Enforce the 2,000-row ceiling while expanding canonical dependency paths. Stop before adding row 2,001 and return the existing controlled error/diagnostic without retaining a larger intermediate collection.
3. Cache the CSRF token for the authenticated session. Clear it on logout/session expiry, refresh once on CSRF-related 403, and retry a mutation at most once.
4. Log source and analysis-temp cleanup failures. Add a bounded sweeper that only touches RiskGraph-owned, prefix-matched temp directories older than a configured threshold.
5. Add concurrency tests with simultaneous scans and downstream AI throttling, exact boundary tests at 1,999/2,000/2,001 rows, CSRF invalidation tests, and stale-temp safety tests.

### Acceptance gate

- Concurrent scans cannot multiply executor thread counts by scan count.
- The row builder never holds more than the configured maximum plus constant bookkeeping.
- A normal UI session does not fetch a CSRF token before every mutation.
- The sweeper cannot delete arbitrary temp paths and cleanup failures are observable.

## Batch 7 — Reduce architectural gravity and strengthen quality gates

### Changes

1. Split `SpringEndpointExtractor` into authorization annotation extraction, dependency-path resolution, changed-surface analysis, and confidence evaluation. Preserve outputs with golden characterization tests before moving logic.
2. Split `SourceScanService` into orchestration, canonical graph-input construction, scan identity, persistence coordination, and validation dispatch.
3. Finish the graph split started in Batch 3. Split `App.tsx` by workspace route/state and split `styles.css` into tokens/primitives, navigation, analysis summary, graph, evidence, account/auth, responsive, and accessibility layers.
4. Move the partial `AuthorizationResolver` into a clearly marked Phase-2/experimental package or keep it disconnected behind an interface until Batch 11. Production annotation extraction must not imply filter-chain support.
5. Apply conventional one-statement-per-line Java formatting to touched files. Use mechanical formatting in isolated commits to keep security diffs reviewable.
6. Add meaningful Python and frontend coverage gates based on the captured baseline, with explicit branch coverage for decision, authorization, validation, and failure-degradation paths. Ratchet thresholds upward; do not chase vanity 100%.
7. Add Playwright flows for login, GitHub connection states, analysis, degraded analysis, validation, graph interaction, logout, stale result/session, and scan access denial.
8. Add automated axe checks to the browser suite and keyboard/focus assertions for graph summary/detail and dialogs.

### Acceptance gate

- Characterization fixtures are byte/logically equivalent before and after each refactor.
- No named gravity-well file retains unrelated responsibilities.
- CI fails on lost security-policy branch coverage, browser workflow breakage, or serious accessibility violations.
- Production capabilities documented by the analyzer exactly match active code.

## Batch 8 — Introduce asynchronous scan jobs and real scan history

### Changes

1. Separate job identity from deterministic result identity. `POST /scans` returns `202` plus a job ID; completed jobs reference the canonical scan ID from Batch 5.
2. Persist `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, and `CANCELLED` state, stage, progress metadata, sanitized reason code, timestamps, and owner.
3. Add status/result endpoints and either bounded polling with backoff or SSE. Do not hold a browser request open for the entire Spoon/graph/AI pipeline.
4. Add an authenticated cancellation endpoint. Propagate cancellation to pending enrichment and analyzer subprocess work where safe; late results must not overwrite cancelled state.
5. Add a membership-filtered, cursor-paginated scan listing with repository, revisions, risk delta, verdict, status, and updated time. Do not expose scans to users without VIEW access.
6. Replace `Open scan by ID` with true `Saved scans` only when the listing API is complete. Include loading, empty, error, pagination, stale, and permission states.
7. Add recovery behavior for platform restarts and idempotent submission of the same canonical input.

### Acceptance gate

- Browser cancellation or navigation does not leave an unobservable synchronous request while work continues indefinitely.
- Job state survives restart in PostgreSQL and terminates deterministically.
- Users can list only scans they may view.
- The dashboard contains no placeholder history UI.

## Batch 9 — Cache source acquisition and analysis safely

### Changes

1. Add an immutable snapshot cache keyed by repository identity and full commit SHA, with bounded size/age, locking, ownership checks, and safe cleanup.
2. Preserve all blob/LFS/file-count/byte limits on cache population and hits. Cached target code remains data and is never executed.
3. Cache extraction/model results by commit plus analyzer/config/sensitivity-policy fingerprint. Prefer cached deterministic extraction records over unsafe reuse of mutable Spoon objects.
4. Use the PR diff and resolved dependency neighborhood to re-extract affected roots. Fall back to full relevant-root extraction whenever resolution is incomplete.
5. Add equivalence tests: cached/incremental and clean/full analysis must produce the same canonical IR, graph delta, risk, diagnostics, and confidence.
6. Benchmark cold, warm, small-diff, and cache-eviction paths and publish hit rate, disk use, scan time, and fallback count.

### Acceptance gate

- Caching cannot change security results or increase confidence.
- Incomplete dependency resolution always falls back or degrades; it never silently skips code.
- Cache storage is bounded and stale cleanup is observable and path-safe.
- Warm small-diff scans show a measured improvement without weakening source non-execution.

## Batch 10 — Phase 2: authorization policy and principal deltas

**Scope gate required:** update the roadmap and `AGENTS.md` before implementation.

### Changes

1. Introduce a versioned deterministic authorization-policy representation alongside backward-compatible `authentication`/`required_role` fields. Represent public, authenticated, role, authority, conjunction/disjunction, and unresolved expressions.
2. Canonicalize supported `@PreAuthorize` expressions into a policy AST. Unsupported expressions lower confidence and force `REVIEW`.
3. Define principal classes `ANONYMOUS`, `AUTHENTICATED`, `ROLE_*`, and `AUTHORITY_*`. Use an explicit configured role hierarchy only; never invent application-specific dominance.
4. Compute `PrincipalSet(before)`, `PrincipalSet(after)`, and `newly_authorized_principals` or an equivalent symbolic policy delta. Anonymous becomes one principal class, not the entire threat model.
5. Add principal/role nodes and `HAS_ROLE`/`CAN_ACCESS` paths without changing the allowed node/edge enums. Update `AGENTS.md` and all versioned contracts together.
6. Score role/authority weakening through authorization change and privilege impact while preserving the published six-factor formula and Risk Before/After/Delta.
7. Add corpus cases for ADMIN→USER, ADMIN→authenticated, authority replacement, removed conjunction, configured role hierarchy, unrelated roles, and unresolved custom expressions.

### Acceptance gate

- Every supported authorization weakening creates an explicit deterministic policy/principal delta.
- Unknown role relationships never produce a confident widening claim or `ALLOW`; they produce `REVIEW` with diagnostics.
- Strengthening changes do not become false positive widenings.
- The LLM has no role in policy parsing, principal sets, graph edges, score, or verdict.

## Batch 11 — Phase 2: ordered `SecurityFilterChain` resolution

**Scope gate required:** update the roadmap and `AGENTS.md` before implementation.

### Changes

1. Replace the unused partial resolver with one integrated implementation that emits ordered matcher rules as `(HTTP method set, normalized path matcher, authorization policy, source location, declaration order)`.
2. Support exact/Ant-style paths, `requestMatchers(HttpMethod.GET, "/foo")`, multiple string paths, `anyRequest()`, `authenticated`, `permitAll`, `hasRole`, `hasAuthority`, and the supported subset of `access(...)` policy AST.
3. Preserve first-match ordering and detect overlapping/shadowed matchers. Never merge conflicting rules into a misleading boolean.
4. Resolve filter-chain policy onto each extracted endpoint and retain ordered source evidence. Custom matchers, custom authorization managers, dynamic values, or unsupported expressions become diagnostics and `REVIEW`.
5. Remove the old dead/partial architecture after integration and update capability documentation to list only tested constructs.
6. Add adversarial ordering, method mismatch, overlap, wildcard, multiple chain, and custom matcher cases. Compare results with real minimal Spring configurations without building untrusted target repositories.

### Acceptance gate

- Method-specific matchers and rule order affect only the endpoints they actually cover.
- Common role/authority rules produce the same policy representation as equivalent annotations.
- Unsupported configurations cannot masquerade as authenticated/public certainty.
- L-05, L-06, L-07, and C-05 close only after corpus evidence demonstrates support.

## Batch 12 — Audit closure and release evidence

### Changes

1. Run unit, integration, contract, corpus, browser E2E, axe, large-graph, concurrency, container-smoke, Trivy, secret/misconfiguration, and deterministic packaging gates from a clean checkout.
2. Re-run the labeled corpus and 2–4 OSS Spring Boot repositories. Publish precision, recall, F1, false positives/negatives, unsupported constructs, performance, graph sizes, and confidence distribution.
3. Verify all four MVP demo scenarios and clearly label Phase-2 scenarios as implemented only if Batches 10–11 passed their scope and quality gates.
4. Produce an audit-closure register with one row per ID: fixed, contained/deferred, preserved invariant, evidence link, test link, and release requirement.
5. Confirm documentation and dashboard wording match actual capabilities, especially validation scope and filter-chain/role support.

### Release gate

- No open P0 item.
- No known supported construct can produce the audited false `ALLOW` cases.
- Every deferred construct is visible as unsupported/incomplete and forces `REVIEW`.
- Dashboard remains responsive at the maximum accepted backend input.
- Non-local deployment is blocked unless isolated validation Docker and service credentials are configured.
- The release evidence bundle is reproducible from a clean checkout.

## Complete finding-to-batch traceability

| Finding | Batch | Closure condition |
|---|---:|---|
| L-01 | 1 | Pattern-aware rules and compound-name regression cases pass |
| L-02 | 1 | Defaulted public repository resource is a path and at least REVIEW |
| L-03 | 0, 10 | MVP forces REVIEW; Phase 2 computes policy widening |
| L-04 | 0, 10 | MVP labels unsupported; Phase 2 models principal reachability |
| L-05 | 0, 7, 11 | Production partial resolver is clearly experimental, then integrated after scope gate |
| L-06 | 0, 11 | Method/path matcher tuples resolve with order |
| L-07 | 0, 10, 11 | Common role/authority/access policy AST resolves deterministically |
| L-08 | 5 | Stable route and separate handler identities coexist |
| L-09 | 5 | Each finding receives its own risk result |
| L-10 | 2 | Zero findings produce NOT_RUN without validator call |
| L-11 | 2 | Validation capability is explicit per finding |
| L-12 | 2 | Aggregation preserves observed status codes only |
| L-13 | 5 | Canonical input/provenance scan identity replaces result serialization |
| S-01 | 4 | Isolated Docker required; host access explicit local-only opt-in |
| S-02 | 4 | Per-service audience credentials reject cross-service use |
| S-03 | 4 | Only owner/admin/share can delegate access |
| S-04 | 0 | Current health fix is contract-tested and closed |
| S-05 | 4 | Deployed demos are precomputed or strictly rate/concurrency limited |
| S-06 | 4 | Trusted proxy modes documented and tested |
| S-07 | 4 | Structured LLM allowlist/scrubber covers future evidence expansion |
| S-08 | 0, 4, 9, 12 | Target non-execution remains a tested invariant |
| S-09 | 0, 4, 12 | Existing probe containment remains a tested invariant |
| S-10 | 0, 4, 12 | Existing web-security controls remain tested |
| P-01 | 3 | Size modes, clustering, worker layout, and ceiling enforced |
| P-02 | 3 | Oversized revisions are not rendered together by default |
| P-03 | 3 | Change details summarized then virtualized/paginated |
| P-04 | 6 | One bounded application executor/queue handles enrichment |
| P-05 | 8 | Persisted cancellable job lifecycle replaces synchronous scan request |
| P-06 | 9 | Bounded immutable commit snapshot cache added |
| P-07 | 9 | Cached/incremental extraction is equivalent to full extraction |
| P-08 | 6 | Expansion stops before exceeding the canonical row limit |
| P-09 | 6 | CSRF cached per session with one safe refresh/retry |
| P-10 | 6, 9 | Cleanup is logged and a path-safe bounded sweeper exists |
| D-01 | 2 | Confirmation copy names the exact probe and source-bound sandbox |
| D-02 | 2 | Incomplete evidence controls the hero message |
| D-03 | 2, 8 | Placeholder removed immediately; real paginated history later |
| D-04 | 2 | Failed refresh permanently marks displayed result stale |
| D-05 | 2 | Every finding shows independent validation status/capability |
| D-06 | 7 | Browser E2E/visual workflow gate covers critical flows |
| D-07 | 3 | Large-graph performance fixtures and budgets run in CI |
| D-08 | 0, 7, 12 | Accessibility is preserved and automated axe coverage added |
| D-09 | 0, 12 | Source/demo/offline identity remains visibly distinct |
| C-01 | 3, 7 | Extractor, scan service, graph/app, and CSS gravity wells split |
| C-02 | 7 | Extractor responsibilities become separate characterized components |
| C-03 | 5, 7 | Scan identity and orchestration/persistence/validation separated |
| C-04 | 5 | Backend policy metadata is the dashboard source of truth |
| C-05 | 7, 11 | Partial resolver marked experimental, then integrated or removed |
| C-06 | 0, 7 | Meaningful Python/frontend/security-branch coverage gates enforced |
| C-07 | 7 | Conventional Java formatting applied in isolated commits |
| C-08 | 4 | Sanitized API errors plus secure structured local diagnostics |
| C-09 | 0, 12 | CI security discipline remains a release invariant |

## Execution rules

- Treat each batch as a separate branch/PR or a short series of scoped commits; do not combine contract changes, mechanical formatting, and behavioral changes in one commit.
- Start each behavior change with a failing adversarial test and end with contract, unit, integration, and relevant end-to-end verification.
- Update schemas, examples, server/client models, persistence, dashboard types, and documentation atomically for any contract change.
- Record before/after evidence for correctness and performance; do not close an audit item on code inspection alone.
- Stop at any scope gate or unexpected metric regression and document the decision instead of broadening functionality silently.
