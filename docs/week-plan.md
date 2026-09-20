# RiskGraph AI Delivery Plan

> **Current week: 12**
> Weeks 1–11 are complete. Week 12 external evidence and the post-0.4.1 platform
> rerun exist, but independent human review remains open.

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
| 12 | Current | Measure accuracy, false positives, performance, and extraction coverage; freeze the demo build | Synthetic metrics and two pinned OSS cases pass the post-0.4.1 rerun; independent human review remains pending |
| 13 | Engineering complete (gate-limited) | Polish graph visualization and generate permanent regression tests from confirmed findings | The dashboard exposes immutable validation provenance and permanent regressions verify confirmation integrity; Python parsing remains blocked on the Week 12 gate |
| 14 | Engineering complete (gate-limited) | Reliability buffer, report/paper, architecture diagrams, and recorded demo | The expanded clean-install gate, recovery procedure, report draft, architecture diagrams, and reproducible recording are implemented; independent review still blocks roadmap advancement |
| 15 | Engineering complete (gate-limited) | Release documentation, deployment packaging, security review, and presentation rehearsal | The public README was reproduced from a fresh clone; main CI passed verification, packaging, and the isolated Linux container smoke test |
| 16 | Planned | Final tagged release, evidence bundle, presentation, and submission | Release artifacts, report, metrics, and demo are archived together |

## Post-MVP Roadmap

These items are planned extensions, not Week 15/16 release blockers. Their detailed
scope and acceptance criteria are tracked in [Pending updates](pending-updates.md).

| Track | Status | Outcome | Promotion gate |
|---|---|---|---|
| Python framework preflight | Implemented, gate pending | Detect Spring Boot, FastAPI, mixed, and unsupported repositories from both requested commits | Unsupported/mixed frameworks produce an explicit notice and no ALLOW/BLOCK verdict; independent Week 12 review remains required |
| Python web analyzer | Experimental; not promoted | Evaluate Python 3 FastAPI analysis behind the existing analyzer boundary and unchanged IR | Router-prefix stitching, cross-file resolution, pinned public evaluation, and the Week 12 gate pass |
| Notification manager | In progress; post-MVP | Notify authorized repository members through in-app and email channels for REVIEW/BLOCK decisions | Delivery-time permission checks and commit/finding dedup are implemented; per-repository controls, PR identity, recovery integration tests, and the Week 12 gate remain open |
| Additional channels/frameworks | Future | Add browser/mobile push and separately evaluated Django/Flask adapters | Each channel passes consent/privacy review; each framework has its own deterministic extraction and evaluation evidence |

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

Week 13-15 engineering work proceeded while the Week 12 external review was
pending; that review is now recorded (see
[Release readiness](release-readiness.md) and the
[fresh-clone rehearsal record](evidence/release-rehearsal-2026-09-17.md)).

The post-MVP plan deliberately does not claim support for every Python repository.
FastAPI is the first framework target; Django and Flask remain separate future
adapters. Notifications are framework-independent and consume only final platform
decision events.

## Required Evidence for Every Analysis

- Repository identity and immutable old/new commit SHAs.
- Analyzer and contract schema versions.
- File, class, method, annotation, and line provenance where available.
- Extraction coverage, unsupported constructs, warnings, and errors.
- Before/after graph path evidence and deterministic risk components.
- AI output labeled as interpretation, never as the source of the verdict.
- Validation target proof showing that tests ran only in the local Docker sandbox.
