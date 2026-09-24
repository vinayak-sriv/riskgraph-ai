# RiskGraph AI

[![CI](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/vinayak-sriv/riskgraph-ai/actions/workflows/ci.yml)

RiskGraph AI reviews the security impact of a Java/Spring Boot pull request. It builds a security graph of the app before and after the change, checks whether the change opened a new path from untrusted input to sensitive data, and returns one of three verdicts — **ALLOW**, **REVIEW**, or **BLOCK** — backed by source locations, graph paths, and transparent scores.

> **Academic research/demo project, not a production scanner.** Never point it at a live, public, or third-party system.

**Evidence-first, not AI-first.** Static analysis produces facts, graph algorithms test reachability, and a deterministic policy computes risk. An optional AI layer can explain the evidence and propose a test, but it cannot invent graph edges, assign scores, or confirm anything — confirmation only happens in approved local Docker sandboxes.

## What it does today

| Change | Result |
|---|---|
| Authorization removed from a protected endpoint | New sensitive path → risk spikes → `BLOCK` (sandbox confirms the HTTP behavior) |
| Cosmetic/text-only change | No new path → `ALLOW` |
| New public endpoint reaching sensitive data | New sensitive path → `BLOCK` |
| Existing public route starts reaching a sensitive resource | New sensitive path → `BLOCK` |

**Supported:** Java/Spring Boot, `@PreAuthorize`-style annotations, the three change classes above, dashboard + GitHub Check + SARIF 2.1.0 + PR summary output.
**Not supported (yet):** `SecurityFilterChain` semantics, IDOR, taint/data-flow analysis, arbitrary Python projects, remote testing. Anything the analyzer can't resolve confidently comes back as low-confidence `REVIEW`, never a silent pass.

## How it works

```text
PR / commit pair → Spoon extraction → versioned endpoint IR
                                            │
                          before/after NetworkX security graphs
                                            │
                        reachability diff + deterministic risk delta
                                    │                   │
                          verdict (ALLOW/REVIEW/BLOCK)   optional AI explanation
                                    │
                          optional local Docker validation
                                            │
                    dashboard · GitHub Check · SARIF · PR summary
```

Everything downstream of extraction consumes the versioned IR in [`contracts/`](contracts/), never raw source or another service's internals.

## Quick start

Needs Docker + Compose, Python 3.12, Git. Commands below are PowerShell.

```powershell
python -m pip install -r requirements-dev.txt

$env:RISKGRAPH_ANALYZER_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:RISKGRAPH_GRAPH_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:RISKGRAPH_AI_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"

python tools/dev/create_mvp_samples.py   # 4 authored scenario repos
python tools/dev/init_auth.py            # local admin credential (kept out of git)
python tools/dev/build_sandboxes.py      # trusted validation images

docker compose --env-file .env -p riskgraph-mvp `
  -f infrastructure/docker-compose.yml `
  -f infrastructure/docker-compose.validation.yml `
  --profile app up -d --build --wait
```

Open [http://localhost:5173](http://localhost:5173) and sign in with the admin account `init_auth.py` created. Run all 4 scenarios + the isolated auth probe:

```powershell
python tools/dev/verify_mvp.py --compose --validation
```

Tear down (keeps the DB volume):

```powershell
docker compose --env-file .env -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app down
```

Full env-var reference, native (non-Docker) startup, Ollama setup, and troubleshooting: [runtime guide](docs/runtime-guide.md).

## Services and responsibilities

| Service | Stack | Owns | Never does |
|---|---|---|---|
| **Platform API** (`services/platform-api`) | Spring Boot | Auth, orchestration, Postgres persistence, scan lifecycle, sharing, dashboard APIs — the only writer to Postgres | Parse Java, score risk, call Ollama, touch Docker |
| **Java analyzer** (`services/java-analyzer`) | Spring Boot + Spoon | `POST /analyze`: takes a repo path + two commit SHAs, emits endpoint/evidence IR (routes, auth annotations, call paths, resource sensitivity, source locations, confidence) | Execute target build scripts, score risk, call AI, write to Postgres |
| **Python analyzer** (`services/python-analyzer`) | FastAPI + `ast` | Same envelope contract, explicit-import router stitching and unambiguous named cross-file calls; dynamic/type-based dispatch remains heuristic | Execute repo code, score risk, write to Postgres |
| **Graph/risk service** (`services/graph-risk-service`) | FastAPI + NetworkX | `POST /graph/delta`, `POST /risk/score`, `POST /analysis` — before/after graphs, reachability, deterministic scoring | Read source, call an LLM, validate HTTP, write to Postgres |
| **AI/validation service** (`services/ai-validation-service`) | FastAPI + Ollama | `POST /ai/analyze` (schema-constrained explanation), `POST /validation/http` (sandbox probe, isolated Docker-in-Docker, no host socket) | Set graph facts, risk, or the verdict; touch remote/public targets |

Run a service's own tests:

```powershell
mvn -f services/platform-api/pom.xml clean verify      # or java-analyzer
python -m pytest tests/graph_risk -q                    # or ai_validation, python_analyzer
```

API definitions live in [`contracts/api/`](contracts/api/); scoring/policy detail is in [risk scoring](docs/risk-scoring.md) and [decision policy](docs/decision-policy.md).

## Dashboard

React + TypeScript, talks only to the platform API (never analyzers, Docker, Ollama, or Postgres directly). Auth'd scan history, synced before/after graph views, deterministic evidence kept visually separate from AI interpretation, validation provenance (image, commit, probe, cleanup status).

```powershell
cd apps/dashboard
npm ci
npm run dev            # http://localhost:5173, expects platform API on :8080
npm test                # or test:coverage, test:e2e
npm run build
```

Set `VITE_PLATFORM_API_BASE_URL` if the platform API isn't on the default port.

## Contracts

`contracts/` is the source of truth between services — nothing consumes another service's internals, only its validated schema:

- `ir/` — endpoint IR, analyzer envelopes, graph deltas, risk results
- `ai/` — schema-constrained AI explanation/test-suggestion output
- `validation/` — HTTP validation requests/results
- `api/` — OpenAPI per service
- `reporting/` — SARIF 2.1.0 (unmodified OASIS schema) and other report formats

Changing a contract means updating the schema, examples, docs, and every consumer — prefer additive fields over renames.

## Docker & isolation

```powershell
docker compose --env-file .env -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app config
```

Boundaries the runtime enforces: only the platform API writes to Postgres · target repos are mounted read-only, their build scripts never run · backend services stay on the internal Compose network · app containers never get the host Docker socket · validation only runs registered, commit-bound sandbox images on an egress-blocked internal network with read-only filesystems, dropped capabilities, non-root users, and mandatory cleanup.

DB schema changes are append-only Flyway migrations in [`infrastructure/db/migrations/`](infrastructure/db/migrations/).

## Samples and evaluation data

- **Authored scenarios** (`python tools/dev/create_mvp_samples.py`) — the 4 cases in the table above, generated fresh each time, never executed.
- **Synthetic risk corpus** (`datasets/risk-corpus/`) — 100 records, half negative, labeled `PROVISIONAL / SYNTHETIC_AI_ASSISTED`. A regression suite, not a benchmark: `python datasets/risk-corpus/tools/corpus.py {generate|validate|evaluate}`.
- **Pinned external Spring cases** (`datasets/external-spring/`) — Spring PetClinic and the Spring REST guide, pinned commits, license-checked, both currently `REVIEW`, with attributable human source review recorded. Independent release sign-off is still pending. Run: `python tools/evaluation/external_spring.py --compose`.

Two negative examples don't support precision/recall claims — this is regression coverage, not a benchmark result.

## Testing

```powershell
python tools/dev/verify_release.py   # full local release gate
```

Tests are organized by responsibility under `tests/` (`contract/`, `graph_risk/`, `ai_validation/`, `python_analyzer/`, `parity/`, `dataset/`, `reporting/`, `release/`, `tools/`); Java tests sit beside their Maven service, dashboard tests under `apps/dashboard`. CI runs Python, Java, dashboard, e2e, full-stack Docker smoke, image vulnerability scanning, and the deterministic release-package gate.

## Dev & release tools

| Command | Purpose |
|---|---|
| `python tools/dev/create_mvp_samples.py` | Generate the 4 authored scenario repos |
| `python tools/dev/init_auth.py` | Create local bootstrap credential |
| `python tools/dev/build_sandboxes.py` | Build/archive validation sandbox images |
| `python tools/dev/verify_mvp.py --compose --validation` | Run all 4 scenarios + validation |
| `python tools/dev/verify_release.py` | Full build/test/contract/corpus/audit gate |
| `python tools/dev/clean_repo.py --dry-run` | Preview removable generated artifacts |
| `python tools/release/package.py --output dist/riskgraph-source.zip --manifest dist/riskgraph-source.manifest.json` | Deterministic, credential-free source archive + SHA-256 manifest |

## Evidence bundles

Historical, commit-specific evidence is under [`docs/evidence/`](docs/evidence/).
The latest fresh-clone rehearsal is
[`docs/evidence/release-rehearsal-2026-09-17.md`](docs/evidence/release-rehearsal-2026-09-17.md).
These records do not replace the pending independent sign-off or final release tag.

## Repository layout

```text
apps/              React/TypeScript dashboard
contracts/         Versioned IR, API, AI, reporting, validation contracts
datasets/          Synthetic regressions + pinned external evaluation metadata
docs/              Architecture, decisions, evidence, runbooks, roadmap
infrastructure/    Docker Compose, Postgres, validation isolation
samples/           Authored scenarios + sandbox application
services/          Java and Python backend services
tests/             Contract, graph, AI, parity, release, reporting tests
tools/             Dev, evaluation, reporting, release utilities
```

## More docs

[Architecture](docs/architecture.md) · [Runtime & API guide](docs/runtime-guide.md) · [Threat model](docs/threat-model.md) · [Risk scoring](docs/risk-scoring.md) · [Decision policy](docs/decision-policy.md) · [IR contract](docs/ir-contract.md) · [Security policy](.github/SECURITY.md) · [Troubleshooting](docs/troubleshooting.md) · [Roadmap / pending work](docs/pending-updates.md) · [Literature mapping](docs/literature-mapping.md) · [Mentor demo guide](docs/mentor-demo.md)

## Status

The Java/Spring Boot pipeline, 4 authored scenarios, isolated validation, dashboard, and reporting outputs are implemented and tested. The prepared release candidate is `v1.0.0-rc.1`; independent sign-off and the final `v1.0.0` tag remain open. FastAPI analysis and the notification manager are post-MVP and not yet promoted — see [pending-updates.md](docs/pending-updates.md).
