# Platform API

The Spring Boot platform API is the public backend boundary for RiskGraph. It owns
authentication, authorization, orchestration, persistence, scan lifecycle, sharing,
validation requests, and dashboard APIs.

## Implemented capabilities

- Session authentication, CSRF protection, role enforcement, and account management.
- Bounded asynchronous scan submission, status polling, cancellation, and recovery.
- PostgreSQL-backed scan jobs, results, graph records, findings, and validation data.
- Owner-scoped scans with explicit `VIEW` and `VALIDATE` sharing.
- Cursor-paginated scan history filtered by the current account's access.
- Contract-authenticated calls to the analyzer, graph/risk, and AI/validation services.
- Demo scenarios and source-bound sandbox validation.

This is the only service allowed to write to PostgreSQL. It does not parse Java
source, compute graph reachability or risk, call Ollama directly, or control Docker.

## Verification

From the repository root:

```powershell
mvn -f services/platform-api/pom.xml clean verify
```

API contracts are published under [`contracts/api`](../../contracts/api/).
