# RiskGraph AI Delivery Plan

> **Current week: 8**
> Weeks 1–7 are complete. Stable repository identities, pair-scoped risk comparison,
> confidence reporting, concurrency preservation, and their regression tests now pass.

## Delivery Principle

Each week must leave a reproducible, tested increment. A feature is not complete
because a screen exists: its inputs, deterministic evidence, failure behavior,
tests, and demo procedure must all work from a clean checkout.

## Week-by-Week Plan

| Week | Status | Outcome | Exit evidence |
|---|---|---|---|
| 1 | Complete | Repository layout, ownership boundaries, Docker Compose skeleton, PostgreSQL baseline, and environment templates | Compose config validates and services have documented boundaries |
| 2 | Complete | Locked JSON schemas, fixtures, migrations, and baseline build/test workflows | Contracts validate; Java, Python, and dashboard builds pass |
| 3 | Complete | Authorization-removal fixture runs through NetworkX graphs, BFS reachability, transparent risk scoring, deterministic verdict, platform API, and dashboard | Automated tests prove 22 → 91, delta +69, and BLOCK |
| 4 | Complete | Read a local Git repository at two commit SHAs, safely materialize both revisions, compute changed Java files/method candidates, and attach repository/commit provenance | Repeatable diff results; invalid paths/SHAs fail cleanly; temporary workspaces are cleaned |
| 5 | Complete | Use Spoon to extract Spring routes, HTTP methods, controllers, and `@PreAuthorize` requirements with file/line evidence | Tests cover class/method mappings, auth removal, multiple paths, overload-safe traversal, and unsupported authorization expressions |
| 6 | Complete | Resolve controller → service → repository → resource paths and emit schema-valid IR for the changed surface | Generated auth-removal commits produce expected before/after IR without hand-authored analyzer fixtures |
| 7 | Complete | Harden graph identity, provenance, reachability, score calibration, and confidence/coverage reporting | Unit, integration, metamorphic, and negative tests demonstrate deterministic behavior |
| 8 | Current | Run one real Java commit pair end to end from source to the dashboard | One-command mentor demo shows source evidence, new attack path, 22 → 91, and BLOCK |
| 9 | Planned | Add Ollama-backed structured explanations and HTTP-test proposals behind a swappable interface | JSON Schema constrained output validates; unavailable Ollama degrades clearly without changing the deterministic verdict |
| 10 | Planned | Execute the proposed authorization test only inside a hardened Docker sandbox and combine evidence into the decision engine | Egress-restricted test run is time/resource bounded, logged, repeatable, and incapable of targeting public systems |
| 11 | Planned | Analyze pull requests through GitHub and publish Checks, SARIF, fingerprints, and concise evidence | A real test PR receives stable inline findings and a rerun does not duplicate them |
| 12 | Planned | Measure accuracy, false positives, performance, and extraction coverage; freeze the demo build | Labeled corpus reports precision/recall/F1 and 2–4 OSS repositories complete within a documented budget |
| 13 | Planned | Polish graph visualization and generate permanent regression tests from confirmed findings | Confirmed findings produce reviewable tests; Python parsing remains conditional on the Week 12 gate |
| 14 | Planned | Reliability buffer, report/paper, architecture diagrams, and recorded demo | Clean-install rehearsal and failure-recovery checklist pass |
| 15 | Planned | Release documentation, deployment packaging, security review, and presentation rehearsal | A new machine can reproduce the demo from the README |
| 16 | Planned | Final tagged release, evidence bundle, presentation, and submission | Release artifacts, report, metrics, and demo are archived together |

## Phase Gates

**Gate A — Contract foundation (passed):** schemas, fixtures, builds, and service
boundaries are stable.

**Gate B — Real-code MVP (target Week 8):** a real Spring Boot commit pair, not a
fixture, produces traceable IR, before/after graphs, risk delta, verdict, and dashboard.

**Gate C — Validated MVP (target Week 10):** Ollama output remains advisory and an
isolated HTTP test confirms or rejects the deterministic hypothesis.

**Gate D — Industry-facing release (target Week 12):** GitHub/SARIF integration,
evaluation metrics, stable fingerprints, performance limits, and OSS evidence exist.

The conditional Python proof of concept may begin only after Gate D passes.

## Required Evidence for Every Analysis

- Repository identity and immutable old/new commit SHAs.
- Analyzer and contract schema versions.
- File, class, method, annotation, and line provenance where available.
- Extraction coverage, unsupported constructs, warnings, and errors.
- Before/after graph path evidence and deterministic risk components.
- AI output labeled as interpretation, never as the source of the verdict.
- Validation target proof showing that tests ran only in the local Docker sandbox.
