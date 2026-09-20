# RiskGraph AI verification report — 10 September 2026

This is the initial verification baseline. The later
[continuation report](continuation-report-2026-09-10.md) records live Ollama,
platform authentication, external-source findings and the updated 110-test run.

The local Java/Spring mentor prototype is running at **http://localhost:5173**.
All four authored source scenarios pass through Compose and PostgreSQL. The
authorization-removal finding is confirmed against its registered Docker image.
Live Ollama and externally reviewed evaluation remain open; this is not a completed
production release or a claim that all roadmap weeks have passed.

## Architecture and implemented milestones

The Spring platform allowlists a local repository and immutable commit pair. Spoon
extracts source-located annotation and call/resource evidence. Only canonical IR
and separate quality metadata reach NetworkX and the versioned deterministic risk
policy. Ollama receives structured evidence and cannot change graph facts, scores,
or verdicts. A fixed HTTP probe runs only inside an isolated local Docker network.
The platform applies the final decision policy, persists through Flyway/PostgreSQL,
and serves the React dashboard. Offline tooling generates Check, PR and SARIF output.

| Milestone | Implementation and verification |
|---|---|
| 1: Graph/risk hardening | Stable scoped identities, sorted nodes/edges, exact added/removed paths, provenance/quality, strict versioned policy, reason codes, calibration and incomplete-analysis tests pass |
| 2: Real source pipeline | `/analyses`, allowlists, immutable SHAs, envelope/IR validation, dependency errors, dashboard source form, evidence and repeatability verified |
| 3: Ollama | Swappable schema-constrained provider, response validation, bounded retry/timeout, redaction and deterministic degraded mode tested; live model response unverified |
| 4: Docker validation | Protected 403/REJECTED and vulnerable 200/CONFIRMED verified with image/commit identity, resource limits, internal network and successful cleanup |
| 5: GitHub | Local event flow, actionable annotations, stable fingerprints and SARIF schema verification pass; opt-in workflow supplied, no remote workflow/publication performed |
| 6: Evaluation | 100 deterministic provisional records, 50% safe/negative, strict schema/integrity/arithmetic/duplicate/split/regeneration checks and metrics implemented; external/human review gate open |
| 7: Four scenarios | All four run from real authored Git commit pairs; canonical IR, paths, risk and deterministic repeats match expected results |
| 8: Local product polish | PostgreSQL normalized persistence, Flyway, Compose builds/health ordering, startup tools, logs/analysis IDs, responsive dashboard, threat model, guides and evidence bundle verified; production/auth/review gates remain open |

The analyzer additionally fixes missed controller dependency rewiring and reports
complex authorization expressions as uncertain. The platform preserves validation
on identical repeat scans. A regression test and live database consistency check
cover that behavior. Mobile navigation/filter names remain accessible; export and
validation controls remain available at narrow widths.

## Exact quality results

`python tools/dev/verify_release.py` passed every check. Frontend tests/build were
also repeated successfully after the final mobile fixes.

| Check | Result |
|---|---|
| Java analyzer | 23 tests; 0 failures, 0 errors, 0 skipped |
| Spring platform | 12 tests; 0 failures, 0 errors, 0 skipped |
| Python contracts, graph/risk, AI/validation, corpus and reporting | 56 passed |
| Frontend Vitest | 3 passed |
| Total | **94 passing tests** |
| JSON Schema/OpenAPI | 16 schema documents checked; all 4 OpenAPI documents and external references validate |
| TypeScript/Vite | Production build succeeds; JS 475.03 kB / 148.35 kB gzip |
| npm production audit | 0 vulnerabilities |
| Compose | Base plus validation override configuration validates; all five application images build and run with PostgreSQL |
| Git whitespace | `git diff --check` passes |

Python service line coverage is **447/464 = 96.34%**: graph engine 97%, risk engine
100%, policy 100%, reasoning 94%, Docker runner 89%. Analyzer JaCoCo coverage is
**524/603 lines = 86.90%**, **217/312 branches = 69.55%**. No platform or frontend
coverage percentage is claimed; their test counts and runtime checks are reported.

Covered failure cases include path/commit/provenance rejection, malformed envelopes,
unavailable and timed-out dependencies, complex/missing extraction, untrusted Docker
targets and image commits, probe timeout/cleanup failure, invalid/free-text AI output,
database failure and CORS. See the test files for the exact bounded assertions.

## Four real-source results

| Scenario | Risk before → after | Delta | New paths | Final verdict |
|---|---:|---:|---:|---|
| Authorization removal | 22 → 91 | +69 | 1 | BLOCK; Docker CONFIRMED |
| Safe cosmetic change | 22 → 22 | 0 | 0 | ALLOW |
| New public sensitive endpoint | 0 → 65 | +65 | 1 | BLOCK |
| Sensitive-resource exposure | 0 → 65 | +65 | 1 | BLOCK |

Every scenario has HIGH confidence and 100% measured call-chain coverage. Seven
expected source-located canonical IR rows match exactly: endpoint, authorization
and sensitivity extraction accuracy are each 7/7 on these authored examples.
Repeated scans match IDs, source evidence, provenance, paths, scores and reason codes.
The sensitive-resource baseline is zero because the affected identity is the newly
reachable route/Payment pair; the distinct Catalog resource is not blended into it.
The four source-run timings were approximately 4.06, 3.08, 3.10 and 2.88 seconds
(mean 3.28 seconds; nearest-rank p95 4.06 seconds). These tiny local fixtures do not
establish general repository performance or real-world vulnerability accuracy.

Protected SHA: `cd798461fbbd4cc37e6e1c35313f726061b913cb`.
Vulnerable SHA: `593c8cf10cb63c35867417273381ffa2dd610af5`.
Their unauthenticated probes returned 403 and the expected 200 JSON marker respectively.
Both cleanup flags are true, and no run-labeled probe containers/networks remain.

PostgreSQL applied V001/V002 and stored four scans, 46 graph nodes, 35 graph edges,
three findings with AI/validation rows. Retrieval survives platform restart unchanged.
After repeat analysis, normalized validation statuses agree with the saved result.
Malformed API input returns FAILED/REVIEW; only the configured dashboard origin
receives CORS permission.

## Provisional evaluation

The corpus contains 60 development, 20 calibration and 20 held-out records grouped
by repository family. All records remain `PROVISIONAL / SYNTHETIC_AI_ASSISTED`.
They form a parameterized behavioral suite, not independently reviewed external data.

| Split | TP | FP | TN | FN | Precision / Recall / F1 | Graph-path exact match / Verdict accuracy |
|---|---:|---:|---:|---:|---|---|
| Development | 18 | 0 | 42 | 0 | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 |
| Calibration | 6 | 0 | 14 | 0 | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 |
| Held-out test | 6 | 0 | 14 | 0 | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 |

These binary metrics measure expected new sensitive paths in synthetic IR. They
are not confirmed exploit counts. FPR/FNR are zero for this suite. IR evaluator
runtime and p95 are recorded per split in the evidence bundle; Spoon extraction
metrics are deliberately null in that evaluator. Its 0.8 coverage is supplied fixture
quality. The separate seven-row source test above measures actual Spoon extraction.

## Startup and walkthrough

```powershell
python -m pip install -r requirements-dev.txt
python tools/dev/create_mvp_samples.py
python tools/dev/build_sandboxes.py --prepare-only
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app up -d --build
python tools/dev/verify_mvp.py --compose --validation
python tools/reporting/submit_event.py contracts/github/examples/pull-request.json samples/generated/mvp-v1/authorization-removal --container-repository /analysis-repositories/mvp-v1/authorization-removal
python tools/dev/verify_runtime.py
```

For the running stack simply open http://localhost:5173. Source inputs are in
`samples/generated/mvp-v1/manifest.compose.json`. Native startup, prerequisites and
every supported environment variable are in [runtime-guide.md](runtime-guide.md).
Follow [mentor-demo.md](mentor-demo.md) for the presentation sequence. Show source
evidence → graph path → weighted score → unconfirmed AI → Docker result → final
verdict, then demonstrate the safe negative example and export JSON.

## Important files and evidence

- `services/java-analyzer/.../{source,extract,service}`: acquisition, Spoon extraction and source envelope.
- `services/graph-risk-service/app/{graph_engine,risk_engine,models,policy}.py`: deterministic graph/risk contract and policy.
- `services/platform-api/.../{SourceScanService,DecisionPolicy,PostgresScanStore}.java`: orchestration, final decision and normalized persistence.
- `services/ai-validation-service/app/{reasoning,validation}.py`: constrained Ollama and Docker-only worker.
- `apps/dashboard/src/{App.tsx,styles.css,types.ts}`: source workflow, states, graphs and responsive accessibility.
- `contracts/`, `infrastructure/`, `.github/workflows/`, `datasets/risk-corpus/`, `tools/dev/`, `tools/reporting/`: schemas, Compose/Flyway, CI, corpus and reproducible tooling.

The [evidence bundle index](../README.md#evidence-bundles) describes the actual scan JSON,
confirmed validation, source extraction metrics, corpus metrics, runtime checks,
offline reports and quality-gate results. Local command logs and the full file list
are in `tmp/release-checks/` (including `git-status.txt`).

The working tree contains both substantial pre-existing work and these additions.
It remains intentionally uncommitted. No project commit, push, PR, Check or PR
comment was created. Generated sample repositories contain isolated authored test
commits and do not change the project's history. The CURRENT WEEK marker is 7.

## Remaining gates and limitations

- The default Ollama instance at localhost:11434 is unavailable. A reachable instance
  and installed model are required to verify live schema-constrained generation.
  Set `OLLAMA_BASE_URL` and `OLLAMA_MODEL`; deterministic analysis already works
  with explicit degraded output. No hosted credential is required.
- Human review of labels and evaluation on 2–4 licensed external Spring repositories
  remain open. Synthetic metrics do not close the Week 12 evaluation gate.
- The sandbox adapter supports only the registered authored authorization-removal
  application. Arbitrary scanned repositories are not built or runtime-confirmed.
- Annotation auth only; complex conditions, multiple dependency paths, missing types
  or other incomplete extraction require review. No full SecurityFilterChain parsing,
  taint/IDOR analysis, second-language support or production-target testing was added.
- Production deployment remains out of scope. The prototype binds host ports to
  loopback, enforces Developer–Analyst–Admin roles plus per-scan ACLs, and confines
  the opt-in validation worker to a dedicated private Docker daemon.
- GitHub publication has only been implemented and validated offline, not dispatched.
  Production dependency audit is clean; the development dependency audit still
  reports two moderate findings. No forced breaking dependency upgrade was applied.

Docker's startup error was repaired by preserving the two stale runtime-socket
directories and restarting with fresh socket paths. Engine 29.7.2 responded and ran
the full validation. Containers, images, volumes and WSL data were not reset.
The earlier approval-review usage-limit block was cleared when work resumed; no
verification action remains blocked by that review.
