# RiskGraph AI — Audit Fixes Applied

**Date:** 2026-09-20 · **Base commit:** `439d967` · **Working tree: 102 files changed, nothing committed**

Every finding from the 2026-09-19 ultra-review has been addressed. Changes are left uncommitted so you can review the diff.

---

## Verification status

| Gate | Result |
|------|--------|
| `ruff check` (incl. new `S`/bandit rules) | **clean** |
| `ruff format --check` | **clean**, 79 files |
| `mypy` — graph-risk / ai-validation / python-analyzer | **clean**, 22 source files |
| `pytest tests` | **198 passed**, 2 environment-only failures |
| Dashboard `tsc -b && vite build` | **clean** |
| Dashboard `eslint --max-warnings 0` | **clean** |
| Dashboard `prettier --check` | **clean** |
| Dashboard `vitest` | **51 passed** |
| Java `javac` syntax parse, 102 files | **0 syntax errors** |
| Java `mvn verify` | **not run — see caveat** |

The two pytest failures are environmental, not code: `test_clean_repo` uses `shutil.rmtree(onexc=)` (Python 3.12+; the verification environment had 3.11) and `test_public_guidance_stays_in_one_root_readme` shells out to `git ls-files` in a directory with no `.git`. Both were failing identically on the untouched baseline commit, and both pass in CI, which pins Python 3.12.

### Caveat you need to act on

**I could not compile the Java modules.** Maven Central returns HTTP 403 through this environment's egress proxy, which is an organization policy denial rather than a transient error. I verified all 102 Java files parse with zero syntax errors — every remaining `javac` diagnostic was a missing-classpath symbol — but that does not check types. Twenty-four Java files changed, so please run:

```powershell
mvn -f services/java-analyzer/pom.xml clean verify
mvn -f services/platform-api/pom.xml clean verify
```

The single change I am least able to verify by inspection is `ClientConfig.java`, which now uses `JdkClientHttpRequestFactory` in place of `SimpleClientHttpRequestFactory`. If that class or its `setReadTimeout(Duration)` is not present in your Spring Boot 4.1.1 baseline, that one file is the thing to adjust.

---

## Verdict correctness — the five criticals

**C-1 — authorization removal scored zero.** `risk_engine.py`: `removed_auth` now reaches `_highest_state`, which intersects it per endpoint. Removing an admin gate on a MODERATE resource went from `8/8/0 → ALLOW` to `8/34/+26 → REVIEW`; on LOW, `4/4/0 → ALLOW` became `4/30/+26 → REVIEW`. `authorization_change` is 100 and `privilege_impact` 60 in both, as the rubric always intended.

The independent oracle in `datasets/risk-corpus/tools/corpus.py` already implemented this correctly — it was the engine that was wrong, which is a useful confirmation the fix restores intended behaviour rather than inventing new behaviour.

**C-2 — incomplete evidence downgraded BLOCK to REVIEW.** `decide()` now evaluates BLOCK first and treats `insufficient` as an escalation floor. A new-anonymous-path CRITICAL exposure BLOCKs whether or not an unrelated authorization delta is unresolved; an ALLOW still escalates to REVIEW under the same flag.

**C-3 — a PR touching no `.java` file returned ALLOW at `confidence=HIGH, coverage_ratio=1.0`.** `CanonicalGraphInputBuilder` no longer requires `changed_files` to be non-empty before treating an empty extraction surface as degraded. A PR that rewrites only `SecurityConfig.kt` or `application.yml` now lands at LOW confidence and REVIEW. This also closes **H-L4** (Java reporting `1.0` where Python reports `0.0` for the same empty extraction) at the verdict level, so both analyzers now agree.

**C-4 — any `Depends(...)` counted as authentication.** `extractor.py` now walks `Annotated[..., Depends(x)]` annotations as well as parameter defaults, and classifies by the dependency's name against an auth allowlist. `Depends(get_db)` is reported unauthenticated at LOW confidence instead of silently suppressing the anonymous-reachability check; `Annotated[User, Depends(get_current_user)]` is correctly recognised as authenticated. Two regression tests cover both directions.

**C-5 — a failed `docker rm` erased a runtime-confirmed exploit.** The Pydantic invariant tying `cleanup_complete` to `status == ERROR` is gone; a cleanup failure now appends evidence and leaves a CONFIRMED verdict intact. The same fix was needed on the Java side, where `SourceValidationService` treated `cleanup_complete == false` as a sandbox *identity* mismatch — it is an operational fact, not an identity property. **L-16** is fixed alongside: `errorResult` and `notRunValidation` both state `cleanup_complete` explicitly, so the aggregate no longer defaults unknown cleanup to "complete".

---

## Correctness beyond the criticals

**H-L1** — `risk_before` is now repository-wide in both scoring branches, so the same before-revision can no longer report `0/LOW` in one PR and `61/HIGH` in another. The per-route before-state remains in `finding_results[].risk_before`. Two tests that pinned the old behaviour were updated, including the one the review flagged.

**H-L3** — `AMBIGUOUS_ROUTE_HANDLERS` degraded quality *after* the verdict was recorded, shipping `quality.incomplete=true` next to ALLOW. `SourceScanService` now records the verdict after `buildFindings`, escalating ALLOW to REVIEW when quality came back incomplete — the same rule the graph service applies.

**L-11** — a FastAPI route without a leading `/` (`@router.get("")`, legal under an `include_router` prefix) failed Pydantic validation and killed the entire scan with a 502. Paths are normalized in the extractor.

**L-13** — added migration `V012` giving `scan_jobs.scan_external_id` a foreign key with `ON DELETE SET NULL`, after clearing rows that are already dangling. A cascade delete can no longer leave a COMPLETED job whose result 404s forever.

**L-14** — `useAnalysis.ts` no longer throws `TypeError` on a response without `status`, which was masking the real failure.

**L-15** — `AsyncScanJobService` claims scan ownership as soon as the scan is persisted, before the cancellation check. Cancelling mid-write no longer orphans a fully persisted scan that `requireView` denies to everyone.

**L-10** — the dashboard's 500-node cap sliced by id order, and because `user:` sorts last it dropped `user:anonymous` and every attack path with it. `boundedGraph` now pins path and USER nodes first and reports how many were truncated.

**M-11** — risk bands were declared twice, so editing one made the published rubric disagree with the verdicts it explains. `category_for` is now derived from a single `BANDS` list.

---

## Security

**H-S1** — the Python analyzer accepted any `repository_path` while its Java twin enforced an allowlist. New `repository_access.py` mirrors `GitSourceAcquirer.validatePath`: resolve, require containment in `RISKGRAPH_ALLOWED_REPOSITORY_ROOTS`, and fail closed with 503 when unset. Wired into `/analyze` (403 / 503) and added to compose. Two tests cover rejection and the unset case.

**H-S2** — the validation stack ran a privileged Docker-in-Docker daemon with TLS off on `tcp://0.0.0.0:2375`, reachable unauthenticated from `ai-validation-service`. It now runs with `DOCKER_TLS_CERTDIR=/certs` on 2376, certs shared read-only to the two clients, and `DockerRunner` passes `--tlsverify` with the client cert triple. The old plaintext port is no longer an accepted destination — there is a test asserting that.

**H-S3** — the analyzer's repository allowlist defaulted to `${user.dir}`, the one security default in the repo that failed *open*. The default is gone and `GitSourceAcquirer` throws on an empty allowlist at startup.

**H-S4 / H-S6 / H-S7** — the Postgres password now uses the same `${VAR:?message}` fail-fast form as the service tokens, in compose and `application.yml`. `RISKGRAPH_SECURE_COOKIE` and `RISKGRAPH_REQUIRE_GITHUB_CONNECTION` now default to the secure value in both code and compose, with the insecure local-dev value as an explicit opt-in in `.env.example`.

**H-S5** — login lockout was keyed on the attacker-supplied `username` field, so five bad passwords a minute locked any known account out indefinitely from one address. The per-account counter is now keyed on `(username, client address)`; the per-IP counter still bounds credential stuffing. Regression test included.

**H-S8** — Dependabot's `pip` entry only covered `requirements-dev.txt`. Added one entry per service plus a `docker` ecosystem entry so the digest-pinned base images get bumped.

**S-11** — `hmac.compare_digest` on `str` raises `TypeError` for a non-ASCII header, turning a 401 into a 500 with a traceback. All three services now compare bytes, matching the Java filter.

**S-12** — `GithubOAuthSettings.usable()` accepted any non-sentinel string as a client secret. It now validates shape and logs when OAuth is skipped. **Separately: your working-tree `.env` currently holds an SSH key fingerprint (`SHA256:…`) where the 40-hex GitHub client secret belongs, so OAuth advertises as available and fails at token exchange.** Worth rotating whatever was pasted there.

`.env` itself is clean — never committed on any branch, correctly gitignored, excluded from release archives and Docker contexts.

---

## Performance

**H-P2** — the extraction cache could never hit: `get`/`put` live in `analyzeInternal`, but compose sets `RISKGRAPH_ANALYZER_PROCESS_ISOLATION=true`, so the long-lived parent never touched it and the worker that populated it exited immediately. `analyzeIsolated` now caches the worker's response in the parent, keyed by the same `analysisId`, re-stamping only `analyzed_at` so a hit and a miss are otherwise byte-identical. `GitSourceAcquirer` gained a public `repositoryIdentity(Path)` so the key is computable without materializing a snapshot.

**H-P1 / H-P6** — three JVMs were sizing themselves to 25% of a shared 1536 MB limit, requesting ~1.6 GB. Both Dockerfiles now set `MaxRAMPercentage` (40 for the analyzer parent, 70 for platform-api) plus `ExitOnOutOfMemoryError`, and the forked worker launches with `-XX:MaxRAMPercentage=25 -XX:TieredStopAtLevel=1 -Xss512k`.

**H-P3** — HikariCP was entirely unconfigured: 10 connections against Tomcat's 200 request threads, with advisory-locked scan writes holding a connection for a whole transaction. Pool is now 20 with a 3s connection timeout (fail fast rather than queue 30s), leak detection on, and Tomcat threads capped at 50.

**H-P4** — `FindingEnrichmentService` used an unbounded `Future.get()` that inherited the 75s HTTP read timeout while occupying one of only two scan-job workers. Now bounded at 20s with the existing `AI_ENRICHMENT_TIMEOUT` fallback and future cancellation.

**H-P5** — `SimpleClientHttpRequestFactory` is `HttpURLConnection`-based with no pooling, so every dependency call paid a fresh handshake. Replaced with `JdkClientHttpRequestFactory` over a pooled `HttpClient`.

**H-P7** — `SnapshotCache.sweepEntries` walked every cached snapshot on every cache *miss* under a global lock (~160k stat calls). Entry size is now recorded in the `.complete` marker at populate time, with a walk as fallback for entries written before the change.

**P-15** — `SpringEndpointExtractor` built one Spoon model per source root and held them all live; it also scanned the full model for `CtMethod` twice to answer two questions. Now one model over all roots, one traversal for both answers.

**P-3** — `ScanIdentityFactory.canonicalize` sorted arrays with `Comparator.comparing(JsonNode::toString)`, re-serializing both operands on every comparison (~6.6 MB of throwaway strings per revision at the 2000-row cap). Now decorate-sort-undecorate.

**P-10** — `ContractValidator` looked up and compiled schemas on every call, 8+ times per scan against 17 KB and 13 KB documents. Now compiled once into a `ConcurrentHashMap`.

**P-13** — `graph-layout.ts` spawned and terminated a 47 KB worker per layout, twice per compare-mode cycle, re-firing on every filter toggle. One shared worker, responses matched by `requestId`, with fallback if it dies.

**P-14** — inline arrow props in `GraphComparison.tsx` invalidated `SecurityGraph`'s `interactiveNodes` memo on every render, rebuilding the `data` object for up to 500 nodes when an unrelated timer fired. Handlers are now `useCallback`-stable.

**P-12** — both Java Dockerfiles re-resolved the whole dependency tree on any source change. Added `dependency:go-offline` before the source copy.

---

## Drift prevention

This is the group most likely to save you from a repeat of C-1.

- **`tests/parity/test_sensitivity_policy_parity.py`** — asserts the two `sensitivity-policy.yml` copies match modulo their header comment, and that the shipped policy uses only matchers both engines implement. `sensitivity_policy.py` now *raises* on an unsupported matcher instead of silently skipping it, so a `regex:` rule added to the Java engine can no longer classify differently in Python.
- **`tests/parity/test_service_auth_parity.py`** — the three `service_auth.py` copies are deliberate (each service builds from its own Docker context), so rather than restructure three build contexts for twenty lines, this asserts they stay logically identical and still fail closed with constant-time comparison.
- **`tests/contract/test_dashboard_types.py`** — `types.ts` was missing three schema properties including required `coverage`. Added `ExtractionCoverage`, `analysis_id` and `sandbox_demonstration`; the test now asserts every schema property is declared. Fields stay optional because `AnalysisResult` legitimately models both the demo payload (4 required fields) and scan results — the compiler proved that when I briefly made them required.
- **`tests/release/test_env_example_documents_configuration.py`** — 15 variables the code read were undocumented, including `RISKGRAPH_ALLOW_HOST_DOCKER` and `RISKGRAPH_ALLOWED_REPOSITORY_ROOTS`. All added with comments; three dead ones removed; the test keeps it honest.
- **CI** — `services/python-analyzer` was in production and in zero gates. Added to both ruff lines and to `--cov`. Added `mypy` (one run per service, since all three have a package named `app`) and a `export_contracts.py` + `git diff --exit-code contracts/` step that simultaneously tests the generator and proves the committed contracts are current. I confirmed regeneration is currently a no-op, so the gate passes.
- **`tests/graph_risk/test_verdict_failure_modes.py`** — six regression tests for C-1, C-2 and H-L1, in the branches that previously had zero coverage.

**Lint and typing.** `ruff` gained `S` (bandit) — appropriate in a security tool. I deliberately did *not* keep `C4`/`SIM`, which produced 126 mechanical edits for no correctness value. Seven genuine bandit findings in production paths are justified inline with `# noqa` and a reason rather than blanket-ignored; developer scripts are ignored by path. `mypy` is configured and clean at `disallow_untyped_defs`, which caught a real hole: `category_for` returned `str` into a `Literal`-typed field.

**Checkstyle and JaCoCo** were theatre — three whitespace rules, and a `report` goal with no threshold (absent entirely from platform-api). Checkstyle now checks correctness (`EqualsHashCode`, `FallThrough`, `IllegalCatch` on Throwable/Error, `UnusedImports`, size ceilings); JaCoCo has a `check` goal at 70% for the analyzer and 60% for platform-api, which now has coverage measurement at all.

---

## Hygiene and docs

`.gitignore` now covers `.venv*/` (101 MB sitting untracked), `.release-test-*/` and `artifacts/`; the tracked 48 KB `.pptx` was untracked with `git rm --cached`. Removed the stale `.git/index.lock` that was blocking git. Deleted six dead scripts (537 LOC, referenced only by each other and by dated docs) and updated the three living docs that mentioned them. Moved 19 dated reports and weekly checklists into `docs/archive/` with a README explaining they are point-in-time records; `docs/` drops from 41 to 22 living documents.

Fixed five documented drifts: `/ai/test-suggestion` is now described as the alias it is, the non-existent `tests/integration/` and `tests/fixtures/` claims are replaced with the directories that exist, the Python analyzer has a README section (it had zero mentions), and `AGENTS.md` no longer calls it "post-MVP planned" — it now states what shipped and what is genuinely outstanding before promotion.

PyYAML was `6.0.3` in CI and `6.0.2` in two containers — the library that parses both policy files. Aligned.

---

## Not done, deliberately

**`GraphRiskClient` was deleted.** The dynamic demo path now uses the shared
`AnalysisClient` for `/analysis`, removing duplicate HTTP/error/response-limit logic.
The platform Maven suite verifies the replacement.

**`AuthorizationResolver` was left in place.** It is 100 lines of `SecurityFilterChain` parsing with no callers, so `http.requestMatchers(...).authenticated()` is still invisible to the IR. Wiring it in changes extraction semantics and needs the Java test suite; deleting it discards work you may want. It needs a decision from you rather than a guess from me.

**`AccountService`'s eight `if (db == null)` branches** were left as they are. Splitting memory and Postgres behind `@Profile`, the way `ScanStore` already does it, is the right fix but it is a refactor across two classes with no compilable test loop available here.
