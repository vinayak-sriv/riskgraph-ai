# Docker release-gate evidence — 2026-09-16

This evidence was produced from the uncommitted audit implementation working tree on
Docker Desktop Engine 29.7.2. It validates the candidate implementation, not a tagged
release or independent Week 12 review.

## Isolated-stack verification

The stack was built and started under the isolated Compose project
`riskgraph-audit` using the validation overlay and `app` profile. All application
services passed their health checks, the private Docker-in-Docker validation daemon
loaded only the approved local sandbox/probe images, and Flyway applied migrations
V001 through V009.

`python tools/dev/verify_mvp.py --compose --validation` passed:

| Scenario | Risk before | Risk after | New paths | Verdict | Validation |
|---|---:|---:|---:|---|---|
| Authorization removal | 22 | 91 | 1 | BLOCK | CONFIRMED |
| Safe cosmetic change | 22 | 22 | 0 | ALLOW | NOT_RUN |
| New public sensitive endpoint | 0 | 65 | 1 | BLOCK | NOT_RUN |
| Sensitive resource exposure | 0 | 65 | 1 | BLOCK | NOT_RUN |

Ollama was intentionally unavailable during this run. AI enrichment used the
documented deterministic degraded mode; graph, risk, validation, and verdict results
remained deterministic and authoritative.

The first cold sandbox boot exceeded the old fixed 15-second readiness window under
the configured 0.5-CPU/256-MB limit. The runner now uses
`RISKGRAPH_VALIDATION_STARTUP_SECONDS`, default 45 seconds and bounded to 5–60.
The full smoke then passed, and invalid-boundary tests were added.

## Vulnerability gate

Trivy 0.74.0 was run with vulnerability scanning, `--ignore-unfixed`, severity
`CRITICAL`, and exit code 1. All 12 images reported zero matching findings:

- Dashboard, platform API, Java analyzer, graph/risk service, and AI/validation service
- PostgreSQL, Docker-in-Docker daemon, and Docker CLI loader
- Protected, vulnerable, and compatibility sandbox applications
- Immutable Python probe base

The scanner image used the locally verified digest
`sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969`.

## Supporting regression gate

The affected Python suite passed 123 tests with 94.77% branch-aware aggregate
coverage against the required 93% threshold. The disposable Compose stack and its
volumes are removed after the final repository checks.
