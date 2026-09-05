# RiskGraph AI

RiskGraph AI analyzes Java/Spring Boot pull requests by comparing security graphs
before and after a change. Deterministic analyzers produce structured evidence,
graph algorithms compute reachability and risk, the AI layer explains the evidence,
and validation confirms findings only against a local Docker sandbox.

## Current Phase

Weeks 1–3 are complete. A mentor-ready fixture vertical slice runs the canonical
authorization-removal IR through real NetworkX graph construction, BFS
reachability, deterministic risk scoring, the platform API, and the dashboard.

Week 4 is current: secure local Git source acquisition, immutable before/after
commits, deterministic Java-file diffing, and provenance. Real Spoon extraction,
AI explanation, and Docker HTTP validation remain future roadmap work and are not
represented as complete. See `docs/week-plan.md` and `docs/week-4-checklist.md`.

## Repository Layout

```text
docs/              Architecture, module boundaries, scope, decisions
contracts/         JSON Schema and OpenAPI contracts shared by services
services/          Backend and analysis service boundaries
apps/              User-facing applications
infrastructure/    Docker Compose, database migrations, sandbox setup
samples/           Sample Spring Boot applications and scenario fixtures
tests/             Contract, integration, and fixture-driven tests
tools/             Developer utilities
```

## Verification

Validate JSON files:

```powershell
Get-ChildItem -Recurse -Filter *.json | ForEach-Object {
  Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json | Out-Null
}
```

Validate Docker Compose:

```powershell
docker compose -f infrastructure/docker-compose.yml config
```

Start PostgreSQL only:

```powershell
docker compose -f infrastructure/docker-compose.yml up postgres
```

Foundation and contract verification:

```powershell
python tools/dev/verify_week2.py
```

Fixture vertical-slice verification:

```powershell
python tools/dev/verify_week3.py
```

Run the fixture vertical-slice services:

```powershell
docker compose -f infrastructure/docker-compose.yml --profile future-services build
docker compose -f infrastructure/docker-compose.yml --profile future-services up
```

## Architecture Rule

Contracts are shared. Services are isolated. The platform orchestrates. Only the
platform writes to PostgreSQL. AI never decides vulnerability truth. Validation
only targets local Docker sandbox applications.

## Local AI

The AI layer uses Ollama through `OLLAMA_BASE_URL`. For this project, Ollama may
run on a Kali VM:

```bash
OLLAMA_BASE_URL=http://<kali-vm-ip>:11434
```

Start Ollama on Kali with:

```bash
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

Then confirm reachability from the service host:

```bash
curl http://<kali-vm-ip>:11434/api/tags
```
