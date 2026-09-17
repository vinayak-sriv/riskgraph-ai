# RiskGraph AI

[![CI](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml)

RiskGraph AI reviews the security impact of a Java and Spring Boot pull request.
It compares the application before and after a change, builds both security graphs,
and asks a practical question:

> Did this change create a new path from an untrusted user to sensitive data?

The answer is backed by source locations, graph paths, transparent scores, and one
of three verdicts: **ALLOW**, **REVIEW**, or **BLOCK**. A React dashboard makes the
before/after result easy to inspect, while GitHub Check, SARIF, and pull-request
summary outputs fit the same evidence into a review workflow.

RiskGraph is evidence-first. Static analysis creates facts, graph algorithms test
reachability, and deterministic policy calculates risk. The optional AI layer can
explain that evidence and propose a test, but it cannot invent graph edges, assign
scores, or confirm a vulnerability. Confirmation is limited to approved local
Docker sandboxes.

> RiskGraph AI is an academic research and demonstration project, not a production
> security scanner. Never use it to test a live, public, or third-party system.

## See it in one minute

RiskGraph currently demonstrates four change-review outcomes:

| Pull-request change | What RiskGraph reports |
|---|---|
| Authorization is removed from a protected export endpoint | A new sensitive path, a large risk increase, and `BLOCK`; the authored sandbox confirms the HTTP behavior |
| A description or other cosmetic text changes | No new path, no risk increase, and `ALLOW` |
| A new public endpoint reaches sensitive data | A new sensitive path, increased risk, and `BLOCK` |
| An existing public route starts reaching a sensitive resource | A new sensitive path, increased risk, and `BLOCK` |

These authored scenarios prove how the platform behaves. They are not a claim of
real-world vulnerability-detection accuracy.

## What is supported today

| Area | Current support |
|---|---|
| Target code | Java and Spring Boot |
| Authorization | Method annotations such as `@PreAuthorize` |
| Change classes | Authorization removal, new public endpoint, sensitive-resource exposure |
| Source evidence | Routes, controllers, services, repositories, resources, source locations, confidence, and diagnostics |
| Graph analysis | Role/endpoint/service/repository/resource paths and BFS reachability |
| Risk | Deterministic before, after, and delta scores with policy metadata |
| AI | Optional schema-constrained explanation and HTTP-test proposal from structured evidence |
| Validation | Registered, commit-bound local Docker sandboxes only |
| Review output | Dashboard, GitHub Check, SARIF 2.1.0, and PR summary |

The MVP does not claim support for `SecurityFilterChain` semantics, IDOR detection,
taint/data-flow analysis, arbitrary Python projects, or remote security testing.
Incomplete and unsupported evidence is surfaced as low confidence or `REVIEW`, never
silently treated as safe. FastAPI analysis is a post-MVP plan and is not presented
as implemented support.

## How it works

```text
Pull request or immutable Git commit pair
                    |
                    v
        Diff analysis + Spoon extraction
                    |
                    v
             Versioned endpoint IR
                    |
                    v
       Before/after NetworkX security graphs
                    |
                    v
        Reachability comparison + risk delta
                    |
          +---------+----------+
          |                    |
          v                    v
 Deterministic verdict   Optional AI explanation
                               |
                               v
                    Local Docker validation
          |
          v
 Dashboard + GitHub Check + SARIF + PR summary
```

The intermediate representation under [`contracts/`](contracts/) is the boundary
between source parsing and every downstream stage. Graph, risk, AI, validation, and
presentation code consume validated contracts rather than another service's
internals.

## Quick start

### Prerequisites

- Docker Desktop or Docker Engine with Compose
- Python 3.12
- Git

The example commands use PowerShell. On another shell, set the same environment
variables using its native syntax.

```powershell
python -m pip install -r requirements-dev.txt

$env:RISKGRAPH_ANALYZER_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:RISKGRAPH_GRAPH_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:RISKGRAPH_AI_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"

python tools/dev/create_mvp_samples.py
python tools/dev/init_auth.py
python tools/dev/build_sandboxes.py

docker compose -p riskgraph-mvp `
  -f infrastructure/docker-compose.yml `
  -f infrastructure/docker-compose.validation.yml `
  --profile app up -d --build --wait
```

The authentication initializer creates a local password file under an ignored
runtime directory. Keep it private and never commit its contents.

Open [http://localhost:5173](http://localhost:5173), then sign in with the local
administrator account created by the initializer. To exercise all four authored
scenarios and the isolated authorization probe, run:

```powershell
python tools/dev/verify_mvp.py --compose --validation
```

Stop the stack without deleting its database:

```powershell
docker compose -p riskgraph-mvp `
  -f infrastructure/docker-compose.yml `
  -f infrastructure/docker-compose.validation.yml `
  --profile app down
```

For native startup, environment settings, Ollama, API examples, troubleshooting,
and safe reset procedures, use the [runtime guide](docs/runtime-guide.md).

## Using the dashboard

The React and TypeScript dashboard talks only to the Spring Boot platform API.
Browser code never connects directly to the analyzers, Docker daemon, Ollama, or
PostgreSQL.

The interface provides:

- authenticated analysis, scan history, cancellation, and account views;
- synchronized before/after security graphs with filters, zoom, pan, full-screen
  inspection, large-graph safeguards, and accessible text alternatives;
- deterministic evidence, risk components, source provenance, extraction
  confidence, and diagnostics separated from optional AI interpretation; and
- source-bound validation records that show the image, commit, probe, outcome, and
  cleanup status.

To work on only the dashboard:

```powershell
cd apps/dashboard
npm ci
npm run dev
npm run lint
npm run format:check
npm test
npm run test:coverage
npm run test:e2e
npm run build
```

The dashboard defaults to `http://localhost:8080` for the platform API. Set
`VITE_PLATFORM_API_BASE_URL` before starting or building it when the platform uses a
different configured endpoint. Design and accessibility notes are in
[`apps/dashboard/REDESIGN.md`](apps/dashboard/REDESIGN.md); optional GitHub sign-in
configuration is in
[`apps/dashboard/GITHUB_AUTH_SETUP.md`](apps/dashboard/GITHUB_AUTH_SETUP.md).

## Services and responsibilities

### Platform API — Spring Boot

The platform API is the public backend boundary. It owns authentication,
authorization, orchestration, PostgreSQL persistence, scan lifecycle, sharing,
validation requests, and dashboard APIs. It supports bounded asynchronous jobs,
status polling, cancellation, recovery, owner-scoped results, explicit `VIEW` and
`VALIDATE` sharing, and cursor-paginated history.

It is the only service allowed to write to PostgreSQL. It does not parse Java,
calculate graph reachability or risk, call Ollama directly, or control Docker.

```powershell
mvn -f services/platform-api/pom.xml clean verify
```

### Java analyzer — Spring Boot and Spoon

`POST /analyze` accepts an allowlisted local repository and two immutable full
commit SHAs. The analyzer emits versioned endpoint and evidence IR containing routes,
annotation authorization, call paths, resource sensitivity, source locations,
coverage, confidence, diagnostics, and provenance.

Target build scripts and repository code are never executed. Source acquisition is
read-only and bounded by file, byte, time, queue, and concurrency limits. The
analyzer does not score risk, call AI, validate HTTP behavior, or write to the
platform database.

```powershell
mvn -f services/java-analyzer/pom.xml clean verify
```

### Graph and risk service — FastAPI and NetworkX

This service consumes validated IR and provides:

- `POST /graph/delta` for before/after graph construction and comparison;
- `POST /risk/score` for deterministic before, after, and delta scores;
- `POST /analysis` for the combined graph, risk, and preliminary verdict; and
- `GET /health` for service health.

It does not read source code, call an LLM, execute repositories, validate HTTP
behavior, or write to PostgreSQL. Authorization uncertainty and incomplete coverage
remain separate from impact scoring.

```powershell
python -m pytest tests/graph_risk -q
ruff check services/graph-risk-service tests/graph_risk
```

### AI and validation service — FastAPI

This service provides optional Ollama explanations and local sandbox validation:

- `POST /ai/analyze` explains structured deterministic evidence;
- `POST /ai/test-suggestion` proposes a schema-constrained authorization test;
- `POST /validation/http` runs an approved probe against a registered sandbox; and
- `GET /health` reports service health.

AI responses are schema constrained and validated. They never set graph facts,
risk, or the final verdict. Validation rejects remote/public targets and defaults to
a private Docker-in-Docker daemon rather than the host socket.

```powershell
python -m pytest tests/ai_validation -q
ruff check services/ai-validation-service tests/ai_validation
```

The API definitions are under [`contracts/api/`](contracts/api/). Risk weights and
decision thresholds are documented in [risk scoring](docs/risk-scoring.md) and the
[decision policy](docs/decision-policy.md).

## Contracts

The contract tree is the source of truth for communication between modules:

- `contracts/ir/` — endpoint IR, analyzer envelopes, graph deltas, and risk results;
- `contracts/ai/` — schema-constrained explanation and test-suggestion outputs;
- `contracts/validation/` — HTTP validation requests and results;
- `contracts/api/` — OpenAPI definitions for service boundaries; and
- `contracts/reporting/` — reporting schemas, including the unmodified OASIS SARIF
  2.1.0 Errata 01 schema.

Contract changes must update schemas, examples, documentation, and consumers.
Additive optional fields are preferred over incompatible renames. Downstream code
consumes IR, not raw source. LLM calls use the AI JSON schemas through Ollama's
constrained `format` parameter.

The SARIF schema source is
<https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json>;
the checked-in file has SHA-256
`c3b4bb2d6093897483348925aaa73af03b3e3f4bd4ca38cef26dcb4212a2682e`.
Copyright and usage terms are maintained by
[OASIS](https://www.oasis-open.org/policies-guidelines/ipr/).

## Docker and runtime boundaries

The Compose `app` profile starts PostgreSQL, the platform API, Java analyzer,
graph/risk service, AI/validation service, and dashboard. The validation overlay
adds a private Docker-in-Docker daemon and approved-image loader.

```powershell
docker compose -p riskgraph-mvp `
  -f infrastructure/docker-compose.yml `
  -f infrastructure/docker-compose.validation.yml `
  --profile app config
```

The runtime enforces these boundaries:

- only the platform API writes to PostgreSQL;
- target repositories are mounted read-only and their build scripts are not run;
- backend services remain on the internal Compose network;
- public development ports bind to loopback by default;
- application containers never receive the host Docker socket;
- validation uses registered, locally built and commit-bound sandbox images;
- probes run on an internal network with blocked external egress; and
- sandboxes use read-only filesystems, dropped capabilities, non-root users,
  resource limits, readiness deadlines, fixed probes, and mandatory cleanup.

Database changes are append-only Flyway migrations in
[`infrastructure/db/migrations/`](infrastructure/db/migrations/). The authored
sandbox application is under [`samples/sandbox/`](samples/sandbox/), and
`tools/dev/build_sandboxes.py` verifies the approved source bindings, builds the
trusted images, and creates an ignored archive for the network-isolated validation
daemon.

## Samples and evaluation data

### Authored Spring scenarios

Generate the four immutable sample repositories with:

```powershell
python tools/dev/create_mvp_samples.py
```

They cover authorization removal, a safe cosmetic change, a new public sensitive
endpoint, and sensitive-resource exposure through an existing route. Generated
repositories are ignored because their commits are test artifacts. RiskGraph reads
their source but never executes their build scripts. IDOR and privilege-expansion
fixtures are design scaffolds, not implemented detection claims.

### Synthetic risk corpus

`datasets/risk-corpus/` contains 100 deterministic records, half safe/negative. All
labels are explicitly `PROVISIONAL / SYNTHETIC_AI_ASSISTED`; no human validation or
real-world accuracy is claimed. The corpus has disjoint development, calibration,
and held-out test repository identities and records IR, paths, risk components,
scores, verdicts, permissible AI evidence, and expected validation state.

```powershell
python datasets/risk-corpus/tools/corpus.py generate
python datasets/risk-corpus/tools/corpus.py validate
python datasets/risk-corpus/tools/corpus.py evaluate --output tmp/corpus-evaluation.json
```

Scenario specifications cover the three MVP findings plus authorization
strengthening, safe refactoring, protected additions, empty changes, ambiguous
authorization, and malformed extraction. The corpus is a behavioral regression
suite, not an external benchmark; policy must not be tuned to the held-out split.

### Pinned external Spring checks

The external evaluation metadata pins Apache-2.0 Spring PetClinic and Spring REST
guide revisions, source hashes, and license hashes. Their build scripts are never
run and third-party source is not redistributed in this repository.

| Source | Reviewed change | Current deterministic limitation |
|---|---|---|
| [Spring PetClinic](https://github.com/spring-projects/spring-petclinic/commit/bb37aad8c332264723817d855e8b3b96b7c392bc) | Owner-search whitespace handling | Helper resolution leaves dependency coverage incomplete |
| [Spring REST guide](https://github.com/spring-guides/gs-rest-service/commit/389429a7345a718c27a77cabc495cf7ea68bbaee) | Java indentation only | No service/repository path exists, so dependency coverage remains incomplete |

Both labels remain provisional and both results remain `REVIEW`. No authorization
annotation does not prove effective runtime access because security-configuration
parsing is outside the MVP. Two negative examples cannot support vulnerability
precision, recall, F1, or sensitivity-accuracy claims.

```powershell
python tools/evaluation/external_spring.py --prepare-only
python tools/evaluation/external_spring.py --compose
```

The Compose run analyzes each pinned case twice, writes ignored local results under
`tmp/external-evaluation`, and never runs HTTP security tests against those
repositories. Independent review instructions are in
[`datasets/external-spring/HUMAN_REVIEW.md`](datasets/external-spring/HUMAN_REVIEW.md).

## Testing and quality gates

Run the complete local release gate from the repository root:

```powershell
python tools/dev/verify_release.py
```

The test tree is organized by responsibility:

- `tests/ai_validation/` — constrained AI behavior, degraded mode, and validation;
- `tests/contract/` — JSON Schema and OpenAPI compatibility;
- `tests/dataset/` — corpus integrity and provenance;
- `tests/graph_risk/` — graph construction, reachability, scoring, and containment;
- `tests/integration/` — cross-module workflows;
- `tests/release/` — packaging, reporting, container, and public-release invariants;
- `tests/reporting/` — GitHub Check, SARIF, and summary output; and
- `tests/tools/` — development and verification utilities.

Java tests live beside their Maven services and dashboard tests live under
`apps/dashboard`. Docker tests use authored local repositories and registered
sandbox images only.

GitHub Actions runs Python, Java, dashboard, browser, Compose smoke, image
vulnerability, source configuration, and deterministic release-package gates. The
latest consolidated local verification record is
[`docs/audit-closure-register-2026-09-16.md`](docs/audit-closure-register-2026-09-16.md).

## Development and release tools

| Command | Purpose |
|---|---|
| `python tools/dev/create_mvp_samples.py` | Generate the four immutable authored commit pairs |
| `python tools/dev/init_auth.py` | Create an ignored local bootstrap credential |
| `python tools/dev/build_sandboxes.py` | Build and archive the trusted sandbox and pinned probe images for isolated validation |
| `python tools/dev/verify_mvp.py --compose --validation` | Exercise the four scenarios and local validation |
| `python tools/dev/verify_release.py` | Run the aggregate build, test, contract, corpus, and audit gate |
| `python tools/dev/clean_repo.py --dry-run` | Preview removable generated artifacts |

Utilities write runtime output to ignored locations unless a documented evidence
file is explicitly generated. They must not alter project history, execute target
repository code, publish GitHub results, or contact external validation targets.

To create a small, deterministic public source archive instead of zipping the
working directory:

```powershell
python tools/release/package.py `
  --output dist/riskgraph-source.zip `
  --manifest dist/riskgraph-source.manifest.json
```

The packager excludes credentials, Git metadata, dependencies, build output, logs,
coverage, runtime files, and nested archives, then independently verifies the output
and reports its SHA-256 digest.

## Evidence bundles

Checked-in evidence is historical and identifies exactly what was verified at the
time. It should not be mistaken for a current release tag or independent approval.

| Bundle | Contents |
|---|---|
| `docs/evidence/mvp-2026-09-10/` | Four source scans, sandbox validation, runtime checks, provisional corpus evaluation, and offline reporting output |
| `docs/evidence/mvp-access-2026-09-10/` | Role/CSRF checks, live Ollama evidence, external-source results, browser checks, and protected validation |
| `docs/evidence/release-candidate-2026-09-13/` | Historical release-gate, external-parser, GitHub CI, and presentation evidence |
| `docs/evidence/release-candidate-2026-09-15/` | Four-scenario Compose rerun, local React workflow, external source rerun, and deterministic packaging evidence |

The latest fresh-clone rehearsal, main-branch CI, source-package identity, and
Docker/image evidence are documented in
[`docs/evidence/release-rehearsal-2026-09-17.md`](docs/evidence/release-rehearsal-2026-09-17.md).
Timing, image IDs, and runtime validation are host-specific; deterministic source,
graph, and risk assertions are tested separately. Synthetic and AI-reviewed labels
remain clearly separated from independent human review.

## Repository layout

```text
apps/              React and TypeScript dashboard
contracts/         Versioned IR, API, AI, reporting, and validation contracts
datasets/          Synthetic regressions and pinned external evaluation metadata
docs/              Architecture, decisions, evidence, runbooks, and roadmap
infrastructure/    Docker Compose, PostgreSQL, and validation isolation
samples/           Authored Spring scenarios and sandbox application
services/          Java and Python backend services
tests/             Contract, graph, AI, release, reporting, and integration tests
tools/             Development, evaluation, reporting, and release utilities
```

## Further documentation

- [Architecture](docs/architecture.md)
- [Technical report draft](docs/report-draft.md)
- [Runtime and API guide](docs/runtime-guide.md)
- [Mentor demo guide](docs/mentor-demo.md)
- [Reproducible demo recording](docs/demo-recording.md)
- [Threat model](docs/threat-model.md)
- [Risk scoring](docs/risk-scoring.md)
- [Decision policy](docs/decision-policy.md)
- [Intermediate representation contract](docs/ir-contract.md)
- [Security review](docs/security-review-2026-09-15.md)
- [Security policy](.github/SECURITY.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Current roadmap and pending updates](docs/pending-updates.md)

## Project status

RiskGraph AI is an actively maintained academic MVP. The Java/Spring Boot pipeline,
four authored scenarios, isolated validation flow, dashboard, reporting outputs, and
release gates are implemented and automated. Independent human review remains the
gate before experimental FastAPI analysis or notification delivery can be promoted.

If you are evaluating the project, start with the one-minute scenario table, run the
Quick Start, and then open the architecture and audit-closure documents for the full
evidence trail.
