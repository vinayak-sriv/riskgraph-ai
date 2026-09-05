# Development Workflow

## Verify Foundation

```powershell
python tools/dev/verify_week2.py
python tools/dev/verify_week3.py
pytest tests/contract
```

## Run PostgreSQL

```powershell
docker compose -f infrastructure/docker-compose.yml up postgres
```

## Build Scaffold Services

The Java services require Java 21 and Maven for local builds. Docker builds use
Maven and JDK 21 images.

```powershell
docker compose -f infrastructure/docker-compose.yml --profile future-services build
```

## Run Python Services Locally

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

## Run Dashboard Locally

```powershell
cd apps/dashboard
npm install
npm run dev
```
