# RiskGraph continuation verification — 10 September 2026

The local prototype is available at **http://localhost:5173**. This continuation
completed live Ollama integration checks and platform authentication, and added
two pinned external-source regressions. It preserves the canonical IR, deterministic
graph/risk/decision boundary, Week 7 marker, and existing uncommitted project work.

## Implemented changes

- **AI/validation:** optional CPU-only Ollama Compose overlay with pinned image and
  local Qwen2.5 0.5B model. Fixed a live llama.cpp grammar expansion failure by
  tightening generation limits while retaining the full public Pydantic contract.
  Evidence is restricted to supplied strings. An incorrect HTTP-method proposal
  found during live review now fails deterministic validation; generation pins the
  supplied method/path. The result remains unconfirmed until Docker validation.
- **Analyzer/platform:** version 0.3.2 preserves zero coverage when a snapshot parser
  fails. Parser errors and empty changed surfaces cannot report HIGH confidence.
  New regression tests reproduce the failure observed on the Spring REST guide.
- **Platform/dashboard:** session sign-in/out, CSRF protection, HttpOnly SameSite
  cookies, BCrypt passwords, additive Flyway V003, and Developer/Analyst/Admin access
  checks. Developers retrieve scans; Analysts analyze/validate; Admins list/create
  accounts. The dashboard supports saved scan IDs and role-aware controls. Local
  bootstrap secrets are generated once in ignored files, not hardcoded or logged.
- **Contracts/tooling:** Platform API 2.0 documents the session/CSRF requirements;
  canonical IR remains 1.0.0. Native startup, PR submission and verification clients
  authenticate. GitHub workflow setup creates a temporary local bootstrap secret;
  no GitHub credentials are needed for the mentor demo.
- **Evaluation:** pinned Apache-2.0 source/license hashes for two official Spring
  repositories; no checkout, target builds or target execution. Expected annotations
  come from AI source review and are explicitly PROVISIONAL, not human-validated.

## Automated results

The complete release verifier passed all nine checks:

| Check | Result |
|---|---|
| Python graph/risk, AI/validation, corpus, contracts/reporting | 61 passed |
| Java analyzer | 25 passed |
| Spring platform, including six live HTTP security tests | 20 passed |
| React dashboard | 4 passed |
| TypeScript and Vite production build | Passed |
| npm production audit | 0 vulnerabilities |
| JSON Schema/OpenAPI validation | Passed within the contract tests |
| Compose configuration, corpus evaluation, git diff check | Passed |

**Total: 110 tests, zero failures.** Python application line coverage is approximately
96.4%; analyzer JaCoCo coverage is 527/606 lines (86.96%) and 228/322 branches (70.81%).
The production dashboard bundle is 480.38 kB JavaScript, 149.79 kB gzip.

Live platform checks verified anonymous rejection, Developer write denial, Analyst
scan/validation access, Admin account creation, required CSRF tokens, logout, and
PostgreSQL-backed account retrieval. Browser checks verified bad-password feedback,
successful Analyst sign-in, retrieval of the persisted CONFIRMED scan, enabled
Analyst controls, no Admin account controls, sign-out, and no horizontal overflow at
1280 px. The temporary browser-test account was removed after sign-out.
The post-restart verifier also passed exact persisted-scan roundtrip, normalized
validation consistency, CORS allowlisting, malformed-request rejection, SARIF
validation and sandbox cleanup. Retained artifacts are under
`docs/evidence/mvp-access-2026-09-10` and exclude local credentials.

## Four source scenarios

`verify_mvp.py --compose --validation --require-ai` passed source-location/IR checks,
schemas, deterministic repeated evidence, all expected decisions, and live AI for
each finding. Safe changes do not invoke the model.

| Scenario | Before → after | Delta | New paths | Verdict | AI |
|---|---:|---:|---:|---|---|
| Authorization removal | 22 → 91 | +69 | 1 | BLOCK, Docker CONFIRMED | AVAILABLE |
| Safe cosmetic change | 22 → 22 | 0 | 0 | ALLOW | NOT_RUN |
| New public sensitive endpoint | 0 → 65 | +65 | 1 | BLOCK | AVAILABLE |
| Sensitive resource exposure | 0 → 65 | +65 | 1 | BLOCK | AVAILABLE |

The four measured source runs took approximately 19.55, 2.63, 8.61 and 8.39 seconds
with the small local CPU model. These are local demo measurements, not a benchmark.
All seven expected source-located IR rows matched, with HIGH extraction confidence
and 100% call-chain coverage on the authored fixtures.
The corrected exposure baseline is scoped to the newly reachable route/Payment pair;
the prior Catalog resource is a distinct identity and does not contribute to it.
The final protected-revision probe returned HTTP 403 / REJECTED with cleanup complete.
After restoring Docker, authenticated retrieval still returned AVAILABLE / CONFIRMED /
BLOCK for the vulnerable scan, and the dashboard returned HTTP 200. All six backend,
database and model health checks passed; the dashboard container was running.

## Important files

| Area | Files |
|---|---|
| AI generation | `services/ai-validation-service/app/reasoning.py`, `infrastructure/docker-compose.ollama.yml` |
| Extraction reporting | `services/java-analyzer/src/main/java/ai/riskgraph/analyzer/service/AnalysisService.java`, `services/platform-api/src/main/java/ai/riskgraph/platform/service/SourceScanService.java` |
| Authentication | `services/platform-api/src/main/java/ai/riskgraph/platform/security/`, `infrastructure/db/migrations/V003__platform_accounts.sql` |
| Dashboard | `apps/dashboard/src/components/AccountPanel.tsx`, `apps/dashboard/src/api.ts`, `apps/dashboard/src/App.tsx` |
| Reproduction | `tools/dev/platform_session.py`, `init_auth.py`, `verify_access.py`, `verify_ollama.py` in the same tools directory |
| External evaluation | `tools/evaluation/external_spring.py`, `datasets/external-spring/manifest.json` |

## External evaluation and honest limits

Petclinic's whitespace change yields both expected GET `/owners` rows. Its helper
call is unresolved, so call-chain coverage is 0%, confidence LOW and verdict REVIEW.
The REST guide's duplicate application classes cause Spoon to fail: neither expected
GET `/greeting` row is emitted, coverage is now correctly 0%, confidence LOW and
verdict REVIEW. Both have zero risk delta and no new paths. Repeated outputs match.

Across these examples, endpoint-plus-auth recall is **2/4 = 50%**, not 100%. The
failure is retained in the evaluation result. Vulnerability precision/recall/F1 and
sensitivity accuracy are unset because two provisional negative cases do not justify
those claims. See the root README's
[pinned external Spring checks](../README.md#pinned-external-spring-checks) and the
immutable manifest.

The existing 100-record synthetic corpus still passes: development TP18/TN42,
calibration TP6/TN14, held-out TP6/TN14, with FP0/FN0, precision/recall/F1 1.0 and
exact graph-path/verdict agreement. These are provisional regression measurements,
not human-reviewed or real-world accuracy estimates.

## Run and demonstrate

Follow the README quickstart and `docs/runtime-guide.md` for prerequisites. Run
`python tools/dev/init_auth.py`, then start Compose with the validation and Ollama
overlays. Pull `qwen2.5:0.5b` once. Sign in as `admin` using the password in
`tmp/local-auth/admin.password`. Native mode uses the same bootstrap file but keeps
accounts/scans in memory. Compose uses PostgreSQL/Flyway persistence.

```powershell
python tools/dev/verify_release.py
python tools/dev/verify_mvp.py --compose --validation --require-ai
python tools/dev/verify_ollama.py
python tools/evaluation/external_spring.py --compose
python tools/dev/verify_access.py
python tools/dev/verify_runtime.py
```

Use the four fixture selector entries first, then run the immutable source pair in
`manifest.compose.json`, inspect before/after graphs and source evidence, and show
AVAILABLE AI with CONFIRMED Docker evidence. Switch to the safe pair to demonstrate
ALLOW. Show Developer view-only access and the exported Check/SARIF/PR summary.

## Remaining boundaries

The small model's explanations are basic and have not passed a quality benchmark.
The two external cases expose real extraction limits; broader human-reviewed
evaluation remains open. Arbitrary applications have no source-bound sandbox adapter.
Account recovery/disablement, audited review overrides, and a rules/repository
administration UI are not implemented. The local worker interfaces and Docker socket
require a trusted developer host; this is not a multi-tenant production deployment.

No project commit, push, PR, Check publication or PR comment was performed. The
workspace remains dirty with preserved earlier work plus this continuation. Human
label review and release/publication approval remain external prerequisites.

Docker Desktop 4.86.0 on this Windows host repeated its stale `dockerInference`
socket startup failure after being stopped. The already verified repair preserved
only the runtime socket directories as `run.riskgraph-backup-20260910-222838` and
`docker-secrets-engine.riskgraph-backup-20260910-222838`, then restarted Docker.
No factory reset, image deletion, WSL unregister or volume deletion was performed.
This host-specific Docker startup issue can recur independently of RiskGraph.
