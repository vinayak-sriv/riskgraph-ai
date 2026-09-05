# Week 3 Fixture Vertical Slice Checklist

Status: **Complete**

## Done Criteria

- [x] `services/platform-api` has a Spring Boot scaffold.
- [x] `services/platform-api` exposes `/health`, `POST /scans`, and
  `GET /scans/{scanId}` stubs.
- [x] `services/java-analyzer` has a Spring Boot scaffold.
- [x] `services/java-analyzer` exposes `/health` and `POST /analyze` stubs.
- [x] `services/graph-risk-service` has a FastAPI scaffold.
- [x] `services/graph-risk-service` builds NetworkX before/after graphs and runs
  real BFS reachability through `/analysis`.
- [x] `services/ai-validation-service` has a FastAPI scaffold.
- [x] `services/ai-validation-service` exposes `/health` and explicit 501 stubs
  for AI and validation routes.
- [x] `apps/dashboard` shows live fixture results from the platform API, including
  before/after paths, risk delta, evidence, and verdict.
- [x] Dockerfiles exist for service/app scaffolds.
- [x] Docker Compose points future service profiles at real scaffold contexts.

## Verified Outcome

- [x] No real Java AST parsing yet.
- [x] Authorization-removal fixture produces risk 22 → 91, delta +69, and BLOCK.
- [x] Graph/risk unit tests, platform tests, dashboard build, dependency checks,
  and Docker integration pass.
- [x] The demo is clearly labeled fixture-driven rather than live source analysis.
- [x] No Ollama client call yet.
- [x] No HTTP validation runner yet.
- [x] No platform database persistence code yet.
