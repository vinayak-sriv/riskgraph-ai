# RiskGraph AI

RiskGraph AI analyzes Java/Spring Boot pull requests by comparing security graphs
before and after a change. Deterministic analyzers produce structured evidence,
graph algorithms compute reachability and risk, the AI layer explains the evidence,
and validation confirms findings only against a local Docker sandbox.

## Current Phase

The Java/Spring pipeline now runs source → Spoon IR → graph comparison → risk →
deterministic verdict → dashboard for all four MVP scenarios. Optional Ollama
explanations degrade safely when unavailable. The registered authorization-removal
sample supports commit-bound, isolated Docker validation. PostgreSQL persistence
uses Flyway migrations; an explicit native `local` profile uses ephemeral storage.

The roadmap marker remains Week 7. Implementation does not close the external
evaluation and review gates: labels are provisional, and broader human-reviewed OSS
evaluation remains open. Live Ollama and platform session/role checks now pass. See
[release readiness](docs/release-readiness.md) and [verification report](docs/verification-report.md).

## Quickstart

Install Docker Desktop with a working Linux engine and Python 3.12. From this root:

```powershell
python -m pip install -r requirements-dev.txt
python tools/dev/create_mvp_samples.py
python tools/dev/init_auth.py
python tools/dev/build_sandboxes.py --prepare-only
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app up -d --build
python tools/dev/verify_mvp.py --compose --validation
```

Open **http://localhost:5173**. Compose exposes services only on loopback. The validation
override starts a dedicated Docker-in-Docker daemon on a private internal network; no
application container receives the host Docker socket. A one-shot loader builds only
the authored sandbox contexts into that daemon. Omit the override to disable live
validation. Existing database volumes are preserved; do not use `down -v` as a repair step.

Sign in as `admin` using the generated password in `tmp/local-auth/admin.password`.
The password stays in that ignored local file; it is never a hardcoded default.
Admin can create Developer (view only) and Security Analyst accounts. Source APIs
require a session and CSRF token; fixture viewing remains public. The native launcher
initializes its own local account using the same file.

Scan access is deny-by-default. The account that starts a scan receives `VALIDATE`
access; administrators can view all scans, and a scan validator may explicitly grant
another provisioned account `VIEW` or `VALIDATE` through `POST /analyses/{id}/access`.

For the verified CPU-only Ollama setup, follow [local AI setup](docs/runtime-guide.md#local-ollama-in-compose).
Two pinned external-source checks are documented in [external evaluation](datasets/external-spring/README.md).

Read [the mentor walkthrough](docs/mentor-demo.md) for exact source inputs,
[configuration](docs/runtime-guide.md) for native startup and environment variables,
and [troubleshooting](docs/troubleshooting.md) for Docker/Ollama/database failures.

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

Weeks 4–6 real-source analyzer verification:

```powershell
python tools/dev/verify_week6.py
```

Run the complete platform without the optional Docker validation worker:

```powershell
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml --profile app up -d --build
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
