# RiskGraph AI

[![CI](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml)

RiskGraph AI is an academic security-analysis platform for Java and Spring Boot pull
requests. It compares an application's security graph before and after a change,
detects newly reachable sensitive resources, calculates a transparent risk delta,
and returns an `ALLOW`, `REVIEW`, or `BLOCK` verdict.

Deterministic analysis is the source of truth. The optional AI layer explains
structured evidence and proposes a validation test; it does not create graph facts,
assign risk scores, or confirm vulnerabilities. Confirmation is limited to HTTP
authorization tests against registered local Docker sandboxes.

> RiskGraph AI is a research and demonstration project, not a production security
> scanner. Do not use it to test live, public, or third-party systems.

## What it provides

- Immutable before/after analysis for local Git commit pairs.
- Spoon-based Spring endpoint, annotation authorization, call-path, repository, and
  resource extraction.
- NetworkX security graphs with deterministic reachability comparison.
- Explainable risk scoring with before, after, and delta values.
- Optional schema-constrained Ollama explanations and HTTP-test suggestions.
- Source-bound validation through an isolated Docker-in-Docker runner.
- A React dashboard for graph comparison, evidence, provenance, scan history, and
  validation results.
- GitHub Check, SARIF 2.1.0, and pull-request summary generation.

## Supported scope

| Area | Current support |
|---|---|
| Target | Java and Spring Boot |
| Authorization | Method annotations such as `@PreAuthorize` |
| Change classes | Authorization removal, new public endpoint, sensitive-resource exposure |
| Graph analysis | Role/endpoint/service/repository/resource paths and BFS reachability |
| AI | Optional explanation and test proposal from structured evidence |
| Validation | Registered local Docker sandboxes only |

`SecurityFilterChain` parsing, taint analysis, IDOR detection, multi-language
analysis, and testing of remote applications are not implemented. Incomplete or
unsupported evidence is reported conservatively instead of being treated as safe.

## Architecture

```text
Git commit pair
      |
      v
Diff + Spoon extraction -> canonical IR -> before/after security graphs
      -> reachability comparison -> deterministic risk delta and verdict
      -> optional AI explanation -> isolated local validation
      |
      v
React dashboard + GitHub Check + SARIF
```

The versioned contracts in [`contracts/`](contracts/) separate source analysis from
graph, risk, AI, validation, and presentation concerns.

## Quick start

### Prerequisites

- Docker Desktop or Docker Engine with Compose
- Python 3.12
- Git

The commands below use PowerShell. On another shell, set the same environment
variables using its native syntax.

```powershell
python -m pip install -r requirements-dev.txt

$env:RISKGRAPH_ANALYZER_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:RISKGRAPH_GRAPH_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:RISKGRAPH_AI_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"

python tools/dev/create_mvp_samples.py
python tools/dev/init_auth.py
python tools/dev/build_sandboxes.py --prepare-only

docker compose -p riskgraph-mvp `
  -f infrastructure/docker-compose.yml `
  -f infrastructure/docker-compose.validation.yml `
  --profile app up -d --build --wait
```

The authentication initializer prints the location of a generated local credential.
Keep that ignored file private and never commit its contents.

Open [http://localhost:5173](http://localhost:5173), or verify the running stack:

```powershell
python tools/dev/verify_mvp.py --compose --validation
```

Stop the stack while preserving its local database:

```powershell
docker compose -p riskgraph-mvp `
  -f infrastructure/docker-compose.yml `
  -f infrastructure/docker-compose.validation.yml `
  --profile app down
```

See the [runtime guide](docs/runtime-guide.md) for native startup, configuration,
Ollama, API usage, troubleshooting, and safe data reset procedures.

## Demonstrated scenarios

| Scenario | Deterministic result |
|---|---|
| Authorization removed from a protected export endpoint | New sensitive path and `BLOCK`; sandbox validation confirms the HTTP behavior |
| Safe cosmetic change | No new path and `ALLOW` |
| New public endpoint reaches sensitive data | New sensitive path and `BLOCK` |
| Existing public route reaches a sensitive resource | New sensitive path and `BLOCK` |

These authored regressions demonstrate system behavior; they are not a claim of
real-world detection accuracy.

## Verification

Run the main local quality gate from the repository root:

```powershell
python tools/dev/verify_release.py
```

GitHub Actions additionally runs the full Java, Python, and dashboard suites,
browser tests, an isolated Compose smoke test, image vulnerability checks, source
configuration checks, and deterministic release-package verification.

Evaluation data is deliberately labeled by provenance. Synthetic and AI-reviewed
records are not presented as independently human-validated results.

## Repository layout

```text
apps/              React and TypeScript dashboard
contracts/         Versioned IR, API, GitHub, and validation contracts
datasets/          Synthetic regressions and external-source evaluation metadata
docs/              Architecture, decisions, evidence, runbooks, and roadmap
infrastructure/    Docker Compose, PostgreSQL, and validation isolation
samples/           Authored Spring scenarios and sandbox application
services/          Java and Python backend services
tests/             Contract, graph, AI, release, and integration tests
tools/             Development, evaluation, reporting, and release utilities
```

## Documentation

- [Architecture](docs/architecture.md)
- [Runtime and API guide](docs/runtime-guide.md)
- [Threat model](docs/threat-model.md)
- [Risk scoring](docs/risk-scoring.md)
- [Decision policy](docs/decision-policy.md)
- [IR contract](docs/ir-contract.md)
- [External Spring evaluation](datasets/external-spring/README.md)
- [Release readiness](docs/release-readiness.md)
- [Troubleshooting](docs/troubleshooting.md)

## Project status

RiskGraph AI is an actively maintained academic MVP. Near-term work is focused on
independent evaluation, reliability, documentation, and release preparation. Larger
scope additions remain separate from the current Java/Spring security-analysis core;
see the [project roadmap](docs/week-plan.md) for details.
