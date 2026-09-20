# Tooling

Week 2 locks the toolchain so Week 3-5 implementation work can start without
arguing about project shape.

## Java Services

- Build tool: Maven.
- Runtime target: Java 21.
- Framework: Spring Boot.

Spring Boot 4.x requires at least Java 17. This repo targets Java 21 for a stable
long-term baseline. If `java -version` reports Java 8, install a newer JDK before
building Java services locally.

## Python Services

- Dependency file: `requirements.txt` per service.
- Framework: FastAPI.
- Models: Pydantic.
- Graph package: NetworkX in `graph-risk-service`.
- HTTP client: httpx in `ai-validation-service`.

## Dashboard

- Build tool: Vite.
- Framework: React + TypeScript.

## Verification

Run the contract and infrastructure checks:

```powershell
pytest tests/contract
docker compose -f infrastructure/docker-compose.yml --profile app config --quiet
```

This validates every contract example against its schema, parses the OpenAPI
YAML, and checks the Docker Compose configuration when Docker is available.
