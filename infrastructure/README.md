# Infrastructure

Infrastructure defines the local runtime boundary for PostgreSQL and the implemented
service scaffolds. The validation sandbox remains isolated behind its own profile.

## Compose File

```powershell
docker compose -f infrastructure/docker-compose.yml config
```

Start PostgreSQL only:

```powershell
docker compose -f infrastructure/docker-compose.yml up postgres
```

Application services and the sandbox are behind explicit profiles:

```powershell
docker compose -f infrastructure/docker-compose.yml --profile future-services config
docker compose -f infrastructure/docker-compose.yml --profile sandbox config
```

Build the application services:

```powershell
docker compose -f infrastructure/docker-compose.yml --profile future-services build
```

Start the fixture vertical-slice services:

```powershell
docker compose -f infrastructure/docker-compose.yml --profile future-services up
```

## Database

The initial PostgreSQL schema is:

```text
infrastructure/db/migrations/V001__initial_schema.sql
```

Only `services/platform-api` may write to this database. Other services return
structured JSON to the platform API.
