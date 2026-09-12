# Development Workflow

For the complete current pipeline use the [runtime guide](runtime-guide.md).
`python tools/dev/verify_release.py` is the full quality gate; the older weekly
commands below remain scoped regression tools.

## Verify Foundation

```powershell
python tools/dev/verify_week2.py
python tools/dev/verify_week3.py
python tools/dev/verify_week6.py
pytest tests/contract
```

## Run PostgreSQL

```powershell
docker compose -f infrastructure/docker-compose.yml up postgres
```

## Build Services

The Java services require Java 21 and Maven for local builds. Docker builds use
Maven and JDK 21 images.

```powershell
docker compose -f infrastructure/docker-compose.yml --profile future-services build
```

## Run Python Services Locally

Internal APIs fail closed unless every process shares a service credential. Set a
long random value in the shell before starting any service manually:

```powershell
$env:RISKGRAPH_SERVICE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
```

```powershell
cd services/graph-risk-service
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8082
```

```powershell
cd services/ai-validation-service
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8083
```

Health endpoints remain public. All other analyzer, graph, and AI service routes
require the `X-RiskGraph-Service-Token` header and should not be published directly.

## Run Dashboard Locally

```powershell
cd apps/dashboard
npm install
npm run dev
```
