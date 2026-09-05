# Dashboard

React + TypeScript dashboard for RiskGraph scan results.

Architecture constraints:
- Reads from `platform-api` only.
- Does not call analyzer, graph-risk, AI-validation, or PostgreSQL directly.

The mentor-demo view calls the platform API at
`GET /demo/authorization-removal` and renders the returned before/after graph,
risk delta, component formula, evidence, and verdict. Configure the browser URL
with `VITE_PLATFORM_API_BASE_URL`; it defaults to `http://localhost:8080`.
