# Platform API

Spring Boot service for orchestration, persistence, users, projects, scans,
decisions, and dashboard APIs.

Architecture constraints:
- This is the only service that writes to PostgreSQL.
- It calls other services through contract-defined APIs.
- It does not parse Java source, compute graph risk, or call Ollama directly.

Current state: the service exposes health/scan scaffolds and a fixture-driven mentor
demo endpoint that orchestrates the graph-risk service for the dashboard. Live Git
scan orchestration and persistence remain planned work.
