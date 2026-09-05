# Dashboard

React + TypeScript dashboard for RiskGraph scan results.

Architecture constraints:
- Reads from `platform-api` only.
- Does not call analyzer, graph-risk, AI-validation, or PostgreSQL directly.

The mentor-demo view calls the platform API at
`GET /demo/authorization-removal` and renders the returned before/after graph,
risk delta, component formula, evidence, and verdict. The graph supports pan,
zoom, fit-to-view, before/after display modes, node-type highlighting, and a
node inspector with incoming/outgoing relationships. The dashboard also supports
attack-path copying, non-destructive result refresh, section-aware navigation,
connection status, a persisted system-aware dark mode, graph-delta badges, and
JSON export. Configure the browser URL with
`VITE_PLATFORM_API_BASE_URL`; it defaults to `http://localhost:8080`.
