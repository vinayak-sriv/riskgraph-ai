# RiskGraph AI — portable project handoff

**Snapshot date:** 2026-09-18  
**Repository:** `vinayak-sriv/riskgraph-ai`  
**Current roadmap marker:** Week 12 — independent external review pending  
**Primary source of operating rules:** repository-root `AGENTS.md`

This document is designed to be pasted into another AI model or given to a new
developer. It explains what RiskGraph AI is, what is implemented, what is not
implemented, the exact current repository state, and the safest next actions.

## 1. Executive summary

RiskGraph AI is an academic security-analysis platform for Java and Spring Boot pull
requests. It compares two immutable Git commits, extracts security-relevant source
evidence, builds before/after security graphs, finds newly reachable paths from an
untrusted user to sensitive resources, calculates a transparent risk delta, and
returns `ALLOW`, `REVIEW`, or `BLOCK`.

The system is deliberately evidence-first:

1. deterministic source analysis produces facts;
2. graph algorithms produce structural reachability results;
3. deterministic policy calculates risk and a preliminary verdict;
4. an optional LLM explains already-established evidence and proposes a test; and
5. only an approved local Docker sandbox may validate the proposed HTTP behavior.

The LLM is never allowed to invent graph edges, set risk scores, or confirm a
vulnerability. RiskGraph never autonomously tests a live, public, or third-party
system.

The Java/Spring Boot MVP is technically mature: source acquisition, Spoon-based
extraction, graph/risk analysis, optional Ollama explanations, isolated validation,
PostgreSQL persistence, dashboard investigation, GitHub/SARIF reporting, CI, release
packaging, authored scenarios, and extensive tests are implemented. Week 13 and Week
14 engineering work was also completed early, but the official roadmap remains at
Week 12 because independent human review of the external-source labels has not been
completed.

## 2. Non-negotiable rules

Any AI or developer continuing this repository must preserve these rules:

- Deterministic analysis is the source of truth. AI only explains and hypothesizes.
- Downstream services consume the versioned IR under `contracts/`, not raw source.
- Always report risk before, risk after, and risk delta.
- Validation may run only against registered, commit-bound local Docker sandboxes.
- Do not run target repository builds or scripts during analysis.
- Do not claim support for a framework, security mechanism, or finding type that is
  only planned or partially scaffolded.
- Uncertain or incomplete evidence must lower confidence and force `REVIEW`; it must
  never silently become `ALLOW`.
- Preserve module boundaries and additive database migrations.
- Keep commits small, reviewed, and explainable because history is part of the
  academic deliverable.
- Read `AGENTS.md` before changing code. It supersedes summaries such as this one.

## 3. Product scope

### Implemented MVP scope

- Target language/framework: Java 21 and Spring Boot, with annotation-based
  authorization such as `@PreAuthorize`.
- Change classes:
  - authorization removal;
  - new public endpoint reaching sensitive data; and
  - an existing public route newly reaching a sensitive resource.
- Source evidence: endpoint, HTTP method, controller, service, repository, resource,
  sensitivity, source location, dependency paths, confidence, coverage, diagnostics,
  analyzer identity, repository identity, and immutable commits.
- Graph model: users, roles, endpoints, controllers/functions, services,
  repositories/databases, data resources, and external services.
- Graph operations: deterministic construction, BFS reachability, before/after
  comparison, stable identities, and new-path evidence.
- Risk: deterministic weighted scoring with per-component and per-finding results.
- AI: optional schema-constrained Ollama explanation and HTTP-test suggestion from
  structured evidence only, with deterministic degraded mode.
- Validation: bounded HTTP authorization probe against an approved local Docker
  sandbox using isolated Docker-in-Docker by default.
- Platform: authentication, role authorization, scan ownership/sharing, asynchronous
  jobs, cancellation, persistence, history, validation orchestration, and reporting.
- User experience: React dashboard with before/after graphs, filtering, source
  evidence, confidence/diagnostics, finding-level risk, validation provenance,
  scan history, account flows, accessibility, and responsive behavior.
- Review output: offline GitHub Check payload, SARIF 2.1.0, and pull-request summary;
  same-repository publication is explicitly opt-in.

### Explicitly not implemented in the MVP

- `SecurityFilterChain`/`HttpSecurity` semantic parsing;
- IDOR detection, taint/data-flow analysis, or privilege-escalation graphs;
- Kubernetes, GraphQL, dependency vulnerability graphing, or policy DSLs;
- general multi-language analysis;
- arbitrary remote or public security testing;
- production-grade multi-tenant deployment claims;
- real-world vulnerability precision/recall claims; or
- outbound notification delivery.

Unsupported security configuration is surfaced through diagnostics, lower confidence,
and `REVIEW`. Phase 2 scaffolds must not be presented as completed detection.

## 4. Architecture and data flow

```text
Git commit pair / pull request
        |
        v
Java analyzer: secure JGit acquisition + diff + Spoon extraction
        |
        v
Versioned endpoint/evidence IR
        |
        v
Python graph-risk service: before/after graphs + BFS + risk delta
        |
        +------------------------+
        |                        |
        v                        v
Deterministic decision      Optional Ollama explanation/test suggestion
        |                        |
        +------------+-----------+
                     v
        Registered local Docker validation
                     |
                     v
Spring platform persistence/orchestration
                     |
                     v
React dashboard + Check + SARIF + PR summary
```

### Service ownership

| Component | Technology | Responsibility |
|---|---|---|
| `services/platform-api` | Java 21, Spring Boot 4.1.1 | Public backend, users, authorization, orchestration, persistence, jobs, sharing, reporting and validation requests |
| `services/java-analyzer` | Java 21, Spring Boot 4.1.1, Spoon, JGit | Bounded source acquisition, diffing, endpoint/auth/call/resource extraction and provenance |
| `services/graph-risk-service` | Python 3.12, FastAPI, NetworkX, Pydantic | Validated IR to graphs, reachability, risk and preliminary verdict |
| `services/ai-validation-service` | Python 3.12, FastAPI, HTTPX, Pydantic | Ollama explanations/test suggestions and isolated local validation |
| `apps/dashboard` | React 19.3, TypeScript 5.9, Vite 8.3 | Authenticated review and evidence investigation interface |
| `infrastructure/db/migrations` | PostgreSQL + Flyway | Append-only persistence schema, currently V001–V009 |
| `contracts` | JSON Schema/OpenAPI/SARIF | Versioned module boundaries and reporting contracts |

Only the platform API writes PostgreSQL. Browser code talks only to the platform API.
The graph/risk service does not read source, and the AI service does not calculate
graph facts or scores.

## 5. Core contracts and policy

The Stage 3 endpoint IR is the cross-module contract:

```json
{
  "endpoint": "/customers",
  "method": "GET",
  "controller": "CustomerController",
  "authentication": true,
  "required_role": "ADMIN",
  "service": "CustomerService",
  "repository": "CustomerRepository",
  "resource": "Customer",
  "sensitivity": "HIGH"
}
```

The platform result adds source evidence, confidence, diagnostics, graphs, path
deltas, finding-specific risk results, handler references, AI state, validation state,
and final decision data. Current relevant versions are analyzer envelope 1.1.0 and
additive platform scan result 1.2.0.

Risk is deterministic:

```text
Risk = 0.25 * Reachability
     + 0.20 * AuthorizationChange
     + 0.20 * DataSensitivity
     + 0.15 * ExternalExposure
     + 0.10 * PrivilegeImpact
     + 0.10 * Exploitability
```

Categories are `0–20 LOW`, `21–40 MODERATE`, `41–60 MEDIUM`, `61–80 HIGH`, and
`81–100 CRITICAL`.

Decision policy v1:

- preliminary `BLOCK`: a new anonymous sensitive path with risk after at least 61;
- `REVIEW`: risk after at least 41, delta at least 21, or incomplete/non-high
  extraction evidence;
- `ALLOW`: no block/review condition applies; and
- failed analysis returns structured `FAILED` with `REVIEW`, never `ALLOW`.

Validation can strengthen a result to `BLOCK`, but AI status never changes risk,
confidence, or verdict. A rejected anonymous HTTP hypothesis does not erase static
evidence.

## 6. Implemented milestones

### Weeks 1–7: foundation and deterministic core

- Monorepo boundaries, ADRs, threat model, Compose, contracts and baseline CI.
- Versioned endpoint/evidence IR and schema validation.
- Fixture-to-graph-to-risk vertical slice.
- Secure immutable source acquisition and deterministic Git diff.
- Spoon extraction for routes, methods, controllers and annotation authorization.
- Controller-to-service-to-repository/resource resolution.
- Stable graph identities, provenance, confidence/coverage and calibrated risk tests.

### Weeks 8–11: end-to-end product MVP

- Real source commit pairs run through source, IR, graph, risk, verdict and dashboard.
- Schema-constrained Ollama provider with safe degraded mode.
- Docker-only validation and deterministic final decision policy.
- Authenticated platform, scan ownership/sharing and asynchronous jobs.
- GitHub Check, SARIF, PR summary, stable fingerprints and source annotations.

### Week 12 engineering

- A 100-record provisional synthetic regression corpus with disjoint splits.
- Two pinned, licensed external Spring source cases with reproducibility checks.
- Performance, extraction coverage and false-positive/negative reporting.
- Clean release packaging and evidence capture.
- Important limitation: external labels remain provisional and the two external cases
  are negative examples. They do not establish real-world vulnerability accuracy.

### Week 13–14 gate-limited engineering

- Validation provenance and confirmed-finding permanent regression support.
- Improved dashboard graph investigation, large-graph handling and accessibility.
- Stronger release verifier, coverage gates, browser checks and demo recording.
- Technical report draft, architecture documentation and release rehearsal.
- Fresh-clone release rehearsal on 2026-09-17 passed all documented release checks
  for the then-committed source.

### Hardening and optimization already completed

- Audience-specific internal service tokens and least-privilege boundaries.
- Isolated validation daemon; no host Docker socket in deployed Compose.
- Bounded queues, concurrency, response sizes, source sizes and 2,000-row graph cap.
- Persistent asynchronous jobs, restart recovery, cursor pagination and cancellation.
- Snapshot and deterministic extraction caches with bounded eviction.
- AI backpressure, schema validation, prompt evidence allowlisting and redaction.
- Route-specific risk results and stable source-handler identities.
- Runtime dependency and dead-code cleanup, including removal of unused JPA packaging.
- Dashboard code/style decomposition and deterministic source packaging.
- CI, container, source archive and dependency vulnerability gates.

## 7. Four mandatory demonstration scenarios

1. Authorization removal: deleting `@PreAuthorize("hasRole('ADMIN')")` from
   `/admin/export` creates a new sensitive path and changes the expected golden risk
   from 22 to 91, ending in `BLOCK`.
2. Safe cosmetic change: no new path, no risk delta, `ALLOW`.
3. New public sensitive endpoint: new anonymous sensitive path, increased risk,
   `BLOCK` or policy-required review.
4. Sensitive-resource exposure: an existing public route newly reaches a sensitive
   resource, increasing risk without claiming IDOR or taint analysis.

All four authored source scenarios are implemented. The authorization-removal
scenario has a commit-bound local sandbox validation path.

## 8. Current Git and working-tree state

At this snapshot:

- branch: `main`;
- local HEAD: `3d3cb96e384ff84e3cf587b58b3a44d7853d0b86`;
- `origin/main`: `7cc0aa44e3d0836a3aeeb0bb6883cc14883224de`;
- local branch is two commits ahead:
  - `b088074 Clarify the GitHub connection setup`;
  - `3d3cb96 Make the dashboard easier to investigate`;
- the working tree contains 23 modified tracked files, four new test files, and this
  new handoff document; and
- no commit or push has been performed for the current hardening batch.

The uncommitted batch is intentional and reviewed. It currently includes:

- dashboard protection against stale session/history responses after logout or
  account switching;
- retryable scan-history pagination errors;
- correct graph relayout behavior, dragged-position preservation and nested Escape
  handling;
- analyzer traversal through resolved helpers;
- service-method authorization extraction with self-invocation containment;
- conservative mixed protected/unprotected dependency-path aggregation;
- cross-process snapshot cache locks and leases;
- right-to-left trusted-proxy parsing with strict IP-literal validation;
- nanosecond-safe scan-history cursors with legacy cursor compatibility;
- batched scan summaries instead of history N+1 reads;
- race-safe PostgreSQL scan-job reuse using `ON CONFLICT DO NOTHING`; and
- indexed finding-risk/handler enrichment rather than repeated scans.

During the 2026-09-18 review, a real analyzer false-negative was found and fixed:
when one repository was reached through both a protected and an unprotected service
method, the resolver could retain only the protected evidence. It now requires
consistent authorization evidence across every resolved traversal and otherwise
fails closed with low confidence and `AMBIGUOUS_SERVICE_AUTHORIZATION`.

Do not discard or overwrite this working tree. Review the complete diff, rerun the
aggregate gate, then commit it as one coherent hardening change if approved.

## 9. Verification status

The current working tree was checked on 2026-09-18 with these results:

- Python: 130 tests passed; aggregate service coverage 96.22% against a 93% floor.
- Java analyzer: 58 tests passed after the mixed-authorization fix; clean Maven
  verify, Checkstyle, JAR and JaCoCo succeeded.
- Platform API: 70 tests passed; clean Maven verify and Checkstyle succeeded.
- Dashboard: 51 unit tests passed.
- Dashboard coverage: 77.26% statements, 69.92% branches, 76.76% functions and
  79.76% lines.
- Browser: five Chromium Playwright workflows passed.
- TypeScript production build, ESLint and Prettier passed.
- Ruff lint and format checks passed.
- Compose configuration passed with the three required non-secret test service
  tokens set.
- Synthetic corpus evaluation passed all 100 records, while retaining its explicit
  provisional/synthetic disclaimer.
- `npm audit --omit=dev` reported zero vulnerabilities.
- `git diff --check` passed.

The aggregate `verify_release.py` run was interrupted at its browser-install stage
because that dependency operation stalled in the managed environment. Earlier Python
and Vite failures in that aggregate run were Windows sandbox permission errors, not
test failures; the same suites passed when rerun with the required process/temp
permissions. Before committing or releasing, run the complete aggregate verifier once
from a normal developer shell so it finishes as a single recorded run, including the
demo-recording stage.

Historical release evidence remains valid only for the commit it names. In
particular, the 2026-09-17 fresh-clone rehearsal predates the current uncommitted
batch and must not be represented as verification of these newer changes.

## 10. What is actually left to do

### Immediate repository work

1. Review all 27 code/test paths in the hardening batch and this handoff document;
   decide whether the documentation should share the hardening commit or use a small
   separate documentation commit.
2. Run `python tools/dev/verify_release.py` uninterrupted in a normal shell.
3. If green, commit the batch with a scoped message such as
   `fix: harden analysis and dashboard reliability`.
4. Push that commit together with the two existing local commits only after explicit
   authorization to publish the repository to the configured GitHub remote.
5. Confirm GitHub Actions on the new `main` SHA and record the run URL/SHA.

### Actual Week 12 release blocker

An independent human reviewer must complete the two external-source decisions in
`datasets/external-spring/HUMAN_REVIEW.md`, including reviewer identity, attribution,
license/source confirmation and final labels. Until that happens:

- keep `CURRENT WEEK: 12`;
- keep external labels provisional;
- do not claim independently reviewed real-world precision, recall or F1;
- do not declare Week 13/14 fully complete; and
- do not begin the conditional FastAPI analyzer implementation.

### Final Java MVP release steps after independent review

1. Update the external manifest/results with the attributed human decisions.
2. Rerun external-source evaluation and the full clean-checkout gate.
3. Rerun the isolated four-scenario Compose MVP and source-bound validation.
4. Generate the deterministic source archive and SHA-256 manifest.
5. Capture final-SHA GitHub CI, package identity and evidence bundle.
6. Obtain release approval, update the roadmap marker, and create the release/tag.
7. Rehearse the demo and module-owner explanations from the exact release SHA.

### Approved post-MVP work, in order

1. Add language/framework preflight and explicit `UNSUPPORTED_FRAMEWORK` results.
2. Add versioned final-decision/notification-event contracts and a transactional
   notification outbox.
3. Implement in-app notification delivery and then opt-in email behind a provider
   interface with permission rechecks, redaction, deduplication and audit records.
4. Build a separate Python 3 FastAPI analyzer using Python AST evidence while
   preserving the Stage 3 IR.
5. Promote FastAPI only after four-scenario parity, provenance, clean-checkout tests,
   local validation and independently reviewed public-repository evaluation.
6. Evaluate Django, Flask, browser/mobile push and other channels separately.

Do not start Kubernetes, general Python support, taint analysis, IDOR, GraphQL, SMS,
or third-party messaging as an incidental extension of another task.

## 11. Known limitations and honest claims

- Annotation absence does not prove runtime public access when unsupported Spring
  security configuration may exist. This is why uncertainty forces `REVIEW`.
- The external evaluation currently contains only two pinned negative examples.
- Synthetic corpus metrics are regression evidence, not field accuracy.
- The verified small Ollama model proves schema-constrained integration, not
  explanation quality.
- Validation supports registered authored fixtures, not arbitrary reviewed projects.
- This is a trusted local academic prototype, not a production multi-tenant scanner.
- Existing Phase 2 fixtures for IDOR or ADMIN-to-USER expansion are design scaffolds,
  not implemented capabilities.

## 12. Safe continuation workflow

From the repository root:

```powershell
# Inspect before editing
git status --short --branch
git diff --check
git diff --stat

# Complete local quality gate
python tools/dev/verify_release.py

# Exercise the complete authored MVP using local Docker only
python tools/dev/verify_mvp.py --compose --validation

# Runtime/access/model checks when their services are configured
python tools/dev/verify_runtime.py
python tools/dev/verify_access.py
python tools/dev/verify_ollama.py

# External pinned sources; never run their builds or security tests
python tools/evaluation/external_spring.py --compose

# Preview removable generated files
python tools/dev/clean_repo.py --dry-run

# Produce a deterministic public archive, never a raw working-directory ZIP
python tools/release/package.py `
  --output dist/riskgraph-source.zip `
  --manifest dist/riskgraph-source.manifest.json
```

Required Compose checks use three distinct service tokens:

- `RISKGRAPH_ANALYZER_SERVICE_TOKEN`
- `RISKGRAPH_GRAPH_SERVICE_TOKEN`
- `RISKGRAPH_AI_SERVICE_TOKEN`

Never commit actual credentials, generated authentication files, `.env`, build
outputs, dependencies, coverage output, caches, temporary repositories, Docker data,
or raw release archives.

## 13. Guidance for another AI model

When continuing this project:

1. Read `AGENTS.md`, then this handoff, then `README.md`.
2. Inspect `git status`, the two unpushed commits, and the uncommitted diff before
   making any edits.
3. Treat user changes as authoritative and do not reset, checkout, or delete them.
4. Use contract schemas and tests to resolve ambiguity; do not invent fields.
5. Keep deterministic facts, risk, confidence, AI interpretation and validation as
   separate concepts in code and user-facing text.
6. Prefer a small tested change over a cross-module redesign.
7. If a request enters post-MVP scope, either defer it or add only the smallest
   explicitly labeled scaffold permitted by `AGENTS.md`.
8. For security findings, cite the exact source, test or contract that demonstrates
   the problem and add a regression test with the fix.
9. Do not commit or push current work without user approval. Publishing to GitHub
   exports source/configuration and requires explicit authorization.
10. After any material change, rerun the relevant focused suite and the aggregate
    release gate before calling it complete.

## 14. Most useful reference files

- `AGENTS.md` — current scope, invariants, roadmap and operating agreement.
- `README.md` — public product guide and supported workflows.
- `docs/architecture.md` — architecture and trust boundaries.
- `docs/decision-policy.md` — deterministic verdict behavior.
- `docs/risk-scoring.md` — risk formula and calibration.
- `docs/ir-contract.md` — IR meaning and evolution rules.
- `docs/release-readiness.md` — release gates and remaining approval.
- `docs/pending-updates.md` — approved post-MVP tracks.
- `docs/week-13-progress.md` and `docs/week-14-progress.md` — completed but
  gate-limited engineering.
- `docs/audit-closure-register-2026-09-16.md` — consolidated logic, security,
  performance, dashboard and quality findings.
- `docs/evidence/release-rehearsal-2026-09-17.md` — last clean-clone release record.
- `datasets/external-spring/HUMAN_REVIEW.md` — the actual open human-review gate.
- `contracts/` — authoritative machine-readable service and result contracts.

## 15. Short copy/paste context for a new model

> RiskGraph AI is an evidence-first Java/Spring Boot PR security analyzer. It securely
> compares immutable commits, extracts annotation-based endpoint/auth/call/resource
> evidence with Spoon, builds before/after NetworkX security graphs, detects new
> anonymous paths to sensitive resources, calculates deterministic before/after/delta
> risk, optionally asks local Ollama to explain structured evidence, and validates
> only against registered local Docker sandboxes. The Spring platform owns auth,
> jobs, PostgreSQL and reporting; React provides the dashboard. The Java MVP is
> engineering-complete, but the roadmap remains at Week 12 until an independent human
> reviews two pinned external Spring labels. Do not claim SecurityFilterChain, IDOR,
> taint, general Python or remote-testing support. As of 2026-09-18, local `main` is
> two commits ahead of `origin/main` and has a reviewed uncommitted hardening batch
> across 27 paths. Current focused verification passes 130 Python, 58 analyzer, 70
> platform, 51 frontend and five browser tests. Before changing anything, read
> `AGENTS.md`, inspect the dirty tree, finish the aggregate release gate, and preserve
> deterministic-analysis/AI/validation boundaries.
