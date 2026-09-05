# MVP Scope

## In Scope

- Spring Boot annotation-based authorization only.
- Authorization removal.
- New public endpoint.
- Sensitive resource exposure.
- Basic role, endpoint, service, repository, database graph.
- BFS/DFS reachability.
- Transparent deterministic risk scoring.
- AI explanation from structured evidence.
- HTTP authorization validation against local Docker sandbox only.
- Dashboard with before/after graph and risk delta.

## Out of Scope

- Multi-language support before the roadmap checkpoint.
- `HttpSecurity` or `SecurityFilterChain` parsing.
- Full IDOR analysis.
- Privilege-reduction/expansion analysis such as `ADMIN` → `USER`.
- Data-flow or taint tracking.
- Kubernetes.
- GraphQL.
- Dependency/library graph edges.
- Historical learning.
- Repo-specific policy DSL.
- Testing live, public, or third-party targets.

Out-of-scope requests should be flagged. If a small placeholder is needed, mark it
clearly as Phase 2 rather than building the full feature.
