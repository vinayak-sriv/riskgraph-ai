# Infrastructure

This directory contains the local Docker Compose topology, PostgreSQL image and
migrations, and the isolated validation overlay.

## Application stack

The `app` profile starts PostgreSQL, the platform API, Java analyzer, graph/risk
service, AI/validation service, and dashboard. The validation overlay adds a private
Docker-in-Docker daemon and an approved-image loader.

```powershell
docker compose -p riskgraph-mvp `
  -f infrastructure/docker-compose.yml `
  -f infrastructure/docker-compose.validation.yml `
  --profile app config
```

Use the root [quick start](../README.md#quick-start) to generate local credentials,
prepare sandbox images, and start the full stack.

## Runtime boundaries

- Only the platform API writes to PostgreSQL.
- Analyzer repositories are mounted read-only.
- Backend services are reachable only on the Compose network.
- Dashboard, platform API, and PostgreSQL bind to loopback by default.
- Validation uses a private daemon and internal network; application services do not
  receive the host Docker socket.
- Containers use resource limits, reduced capabilities, and non-root users wherever
  the upstream initialization contract permits.

Database changes are append-only Flyway migrations under [`db/migrations`](db/migrations/).
Operational details are in the [runtime guide](../docs/runtime-guide.md).
