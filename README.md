# RiskGraph AI

[![CI](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml)

RiskGraph AI is a security-analysis platform for Java and Spring Boot pull
requests. It compares the application security graph before and after a change,
finds newly reachable sensitive resources, calculates a transparent risk delta,
and returns an `ALLOW`, `REVIEW`, or `BLOCK` verdict.

Deterministic analysis is always the source of truth. The optional AI layer only
explains structured evidence and proposes a validation test; it never creates
graph facts, assigns risk, or confirms a vulnerability. Confirmation can happen
only through an HTTP authorization test against an isolated local Docker sandbox.

## How the pipeline works

```text
GitHub pull request
        |
        v
Git diff -> Spoon static analysis -> canonical IR
        -> before/after security graphs -> BFS reachability
        -> deterministic risk delta -> optional Ollama explanation
        -> local Docker validation -> ALLOW / REVIEW / BLOCK
        |
        v
React dashboard + GitHub Check + SARIF + pull-request summary
```

The canonical intermediate representation (IR) separates source analysis from
the graph, risk, AI, and validation stages. Every evidence-bearing result includes
repository and commit identity, source locations, analyzer version, and extraction
coverage or diagnostics.

## MVP scenarios

The reproducible Java/Spring demo covers four source-level scenarios:

| Scenario | Expected result |
|---|---|
| Authorization removed from a protected export endpoint | Risk `22 -> 91`, delta `+69`, `BLOCK`; registered Docker test confirms the finding |
| Safe cosmetic change | Risk remains `22`, no new path, `ALLOW` |
| New public endpoint reaches sensitive data | Risk `0 -> 65`, delta `+65`, `BLOCK` |
| Existing public route begins reaching a sensitive resource | Risk `0 -> 65`, delta `+65`, `BLOCK` |

These are authored regression scenarios, not a claim of real-world accuracy.

## Current roadmap status

The repository is currently at **Week 12** of the 16-week roadmap:

- Weeks 1-11 are complete and have reproducible implementation and CI evidence.
- Week 12 has a provisional evaluation corpus and two pinned, licensed Spring
  repositories, but independent human review and the post-analyzer-0.4.1
  evaluation rerun are still required.
- Gate-independent Week 13 work has started. The dashboard now exposes immutable
  validation provenance, and confirmed findings can become permanent regression
  tests.
- Week 13 is not complete, and the conditional Python proof of concept remains
  blocked until the Week 12 review gate passes.

See the [delivery plan](docs/week-plan.md), [Week 13 progress](docs/week-13-progress.md),
and [release-readiness gates](docs/release-readiness.md) for the exact evidence and
remaining work.

## Quick start with Docker

### Prerequisites

- Docker Desktop using Linux containers and Docker Compose 2.33.1 or newer
- Python 3.12
- Git

From the repository root, generate an internal service token for the current
PowerShell session:

```powershell
$env:RISKGRAPH_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
```

Use `.env.example` only when you need persistent configuration. Copy it to the
ignored `.env` file and replace every placeholder before using `--env-file .env`.
When Compose connects to Ollama running on the host, set
`OLLAMA_BASE_URL=http://host.docker.internal:11434`; `localhost` is correct only
for native services running outside containers.

Then prepare the authored repositories, initialize the local administrator, and
start the isolated stack:

```powershell
python -m pip install -r requirements-dev.txt
python tools/dev/create_mvp_samples.py
python tools/dev/init_auth.py
python tools/dev/build_sandboxes.py --prepare-only
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app up -d --build --wait
python tools/dev/verify_mvp.py --compose --validation
```

Open [http://localhost:5173](http://localhost:5173) and sign in as `admin` with
the generated password in `tmp/local-auth/admin.password`. This ignored file is
the source bootstrap secret. Compose copies it into the private `riskgraph-auth`
named volume, which the platform mounts read-only at runtime; PostgreSQL stores
only its BCrypt hash. Stopping Compose preserves both named volumes, and deleting
the local file alone does not remove the runtime copy or reset an existing account.

The validation overlay creates a private Docker-in-Docker daemon. Application
containers never receive the host Docker socket, and tests cannot target a live,
public, or third-party system.

To stop the stack without deleting its database volume:

```powershell
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app down
```

Do not use `down -v` as a routine repair step because it deletes persisted local
data. For native Java, Python, and Node startup, see the
[runtime and API guide](docs/runtime-guide.md).

## Local endpoints

| Component | Address | Exposure |
|---|---|---|
| Dashboard | [http://localhost:5173](http://localhost:5173) | Loopback only |
| Platform API | [http://localhost:8080](http://localhost:8080) | Loopback only |
| PostgreSQL | `127.0.0.1:5432` | Loopback only |
| Java analyzer | `java-analyzer:8081` | Compose network only |
| Graph and risk service | `graph-risk-service:8082` | Compose network only |
| AI and validation service | `ai-validation-service:8083` | Compose network only |

## Access and security model

- `Developer`: view access only.
- `Security Analyst`: start analysis and validation.
- `Admin`: manage local accounts and view all scans.
- Source scans are private by default. The account that starts a scan receives
  validation access and can explicitly share `VIEW` or `VALIDATE` access.
- Static analysis accepts allowlisted local repositories and immutable 40-character
  commit SHAs. It never executes a target repository's build scripts.
- The MVP analyzes annotation-based Spring authorization such as `@PreAuthorize`.
  It does not claim `SecurityFilterChain`, taint-flow, IDOR, multi-language, or
  production-target testing support.

## Local AI with Ollama

Ollama is optional. If it is unavailable or returns invalid content, the pipeline
continues with an explicit deterministic degraded result. Configure it through
environment variables instead of hardcoding a host or model.

For native services:

```text
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3.1:8b
```

For Compose connecting to Ollama on the host:

```text
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_MODEL=llama3.1:8b
```

Every AI call uses schema-constrained output and the response is validated before
display. For the verified Compose overlay and a Kali VM setup, follow the
[local Ollama guide](docs/runtime-guide.md#local-ollama-in-compose).

## Verification

Run these commands from the same shell in which `RISKGRAPH_SERVICE_TOKEN` was set.
When checking an already-running stack, its token must match the value used to
start that stack. The same primary checks used by GitHub Actions can be run locally:

```powershell
ruff check services/graph-risk-service services/ai-validation-service tests tools datasets/risk-corpus/tools
ruff format --check services/graph-risk-service services/ai-validation-service tests tools datasets/risk-corpus/tools
python -m pytest tests -q --cov=services/graph-risk-service/app --cov=services/ai-validation-service/app
mvn -f services/java-analyzer/pom.xml clean verify
mvn -f services/platform-api/pom.xml clean verify
npm --prefix apps/dashboard ci
npm --prefix apps/dashboard run build
npm --prefix apps/dashboard run lint
npm --prefix apps/dashboard run format:check
npm --prefix apps/dashboard test
npm --prefix apps/dashboard audit --omit=dev
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app config --quiet
```

Run the aggregated local build, contract, corpus, and audit gate:

```powershell
python tools/dev/verify_release.py
```

With the Compose stack running, the remaining runtime and evaluation checks are:

```powershell
python tools/dev/verify_mvp.py --compose --validation
python tools/dev/verify_runtime.py
python tools/dev/verify_access.py
python tools/dev/verify_ollama.py
python tools/evaluation/external_spring.py --compose
```

The Ollama check requires a reachable instance and installed model. The external
evaluation remains provisional until an independent reviewer completes its labels.
Create the deterministic release archive and manifest separately:

```powershell
python tools/release/package.py --output dist/riskgraph-source.zip --manifest dist/riskgraph-source.manifest.json
```

CI also performs a full isolated container smoke test, vulnerability scans of the
built images and source configuration, and deterministic release-archive
verification.

## GitHub pull-request integration

The `RiskGraph PR analysis` workflow produces a Check payload, SARIF 2.1.0 report,
stable finding fingerprints, source annotations, and a concise pull-request
summary. Fork pull requests are analyzed without write permissions. Publishing a
Check or comment for a same-repository pull request is opt-in through the repository
variable `RISKGRAPH_PUBLISH=true`.

Local verification never modifies this project's Git history, pushes branches,
opens pull requests, or publishes GitHub comments. Fixture preparation may create
or update isolated synthetic Git repositories under `samples/generated`.

## Repository layout

```text
apps/              React and TypeScript dashboard
contracts/         Versioned IR, API, GitHub, and validation contracts
datasets/          Provisional evaluation data and permanent regressions
docs/              Architecture, decisions, evidence, runbooks, and roadmap
infrastructure/    Docker Compose, PostgreSQL, and isolated validation setup
samples/           Authored Spring Boot scenarios and sandbox application
services/          Java platform/analyzer and Python graph/AI services
tests/             Contract, graph, AI, evaluation, and integration tests
tools/             Development, reporting, evaluation, and release utilities
```

## Documentation

- [Mentor demo walkthrough](docs/mentor-demo.md)
- [Runtime, configuration, and API guide](docs/runtime-guide.md)
- [Architecture](docs/architecture.md)
- [Threat model](docs/threat-model.md)
- [Risk scoring](docs/risk-scoring.md)
- [Decision policy](docs/decision-policy.md)
- [External Spring evaluation](datasets/external-spring/README.md)
- [Troubleshooting](docs/troubleshooting.md)

RiskGraph AI is currently a local academic MVP, not a production security scanner.
