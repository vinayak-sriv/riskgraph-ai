# RiskGraph AI Delivery Plan

> **Current week: 12**
> Weeks 1–11 are complete. Week 12 external evidence exists, but independent human
> review and the post-0.4.1 platform summary rerun remain open.

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
| 8 | Complete | Run one real Java commit pair end to end from source to the dashboard | Four real-source demo scenarios pass in the full Compose verifier |
| 9 | Complete | Add Ollama-backed structured explanations and HTTP-test proposals behind a swappable interface | Schema-constrained output, deterministic degraded mode, and provider backpressure are tested |
| 10 | Complete | Execute the proposed authorization test only inside a hardened Docker sandbox and combine evidence into the decision engine | Docker-only validation and deterministic final policy pass in the Linux container exercise |
| 11 | Complete | Analyze pull requests through GitHub and publish Checks, SARIF, fingerprints, and concise evidence | PR #18 publishes a schema-valid evidence artifact, neutral Check, SARIF, summary, and exact head identity |
| 12 | Current | Measure accuracy, false positives, performance, and extraction coverage; freeze the demo build | Synthetic metrics and two pinned OSS cases exist; independent human review and a post-0.4.1 platform rerun remain pending |
| 13 | Started (gate-limited) | Polish graph visualization and generate permanent regression tests from confirmed findings | The dashboard exposes immutable validation provenance and permanent regressions verify confirmation integrity; Python parsing remains blocked on the Week 12 gate |
| 14 | Planned | Reliability buffer, report/paper, architecture diagrams, and recorded demo | Clean-install rehearsal and failure-recovery checklist pass |
| 15 | Planned | Release documentation, deployment packaging, security review, and presentation rehearsal | A new machine can reproduce the demo from the README |
| 16 | Planned | Final tagged release, evidence bundle, presentation, and submission | Release artifacts, report, metrics, and demo are archived together |

## Phase Gates

**Gate A — Contract foundation (passed):** schemas, fixtures, builds, and service
boundaries are stable.

**Gate B — Real-code MVP (passed):** a real Spring Boot commit pair, not a
fixture, produces traceable IR, before/after graphs, risk delta, verdict, and dashboard.

**Gate C — Validated MVP (passed):** Ollama output remains advisory and an
isolated HTTP test confirms or rejects the deterministic hypothesis.

**Gate D — Industry-facing release (current Week 12 gate):** GitHub/SARIF integration,
evaluation metrics, stable fingerprints, performance limits, and OSS evidence exist.

The conditional Python proof of concept may begin only after Gate D passes.

Week 13 work that does not depend on Gate D may proceed while Week 12 review is
pending. See [Week 13 progress](week-13-progress.md) for the implemented subset and
the exact remaining gate.

## Required Evidence for Every Analysis

- Repository identity and immutable old/new commit SHAs.
- Analyzer and contract schema versions.
- File, class, method, annotation, and line provenance where available.
- Extraction coverage, unsupported constructs, warnings, and errors.
- Before/after graph path evidence and deterministic risk components.
- AI output labeled as interpretation, never as the source of the verdict.
- Validation target proof showing that tests ran only in the local Docker sandbox.
