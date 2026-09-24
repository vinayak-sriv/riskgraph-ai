# Release-candidate evidence — 2026-09-23

This record covers the uncommitted `v1.0.0-rc.1` candidate working tree. It is
technical verification, not independent release sign-off or a final Git tag.

## Runtime verification

- The full Compose stack and isolated Docker-in-Docker validator were healthy.
- All four Java MVP scenarios produced their expected risk scores, paths, and verdicts.
- Java authorization removal: risk 22 → 91, `BLOCK`, runtime `CONFIRMED`.
- Python authorization removal: risk 18 → 87, `BLOCK`, runtime `CONFIRMED`.
- PostgreSQL restart persistence, normalized validation state, CORS, malformed-request
  failure, Check/SARIF reporting, and sandbox cleanup passed.
- Live Ollama returned `AVAILABLE` / `SCHEMA_VALIDATED` with `qwen2.5:0.5b`; the model
  remained non-authoritative and proposed the constrained HTTP authorization test.

## External evaluation

- Both pinned FastAPI cases matched 52/52 reviewed endpoint/authentication rows and
  remained fail-closed at `REVIEW` because extraction confidence is `LOW`.
- Both pinned Spring cases matched 4/4 reviewed endpoint/authentication rows and
  remained fail-closed at `REVIEW`.
- Dated summaries are stored in `datasets/external-fastapi/` and
  `datasets/external-spring/`. Independent FastAPI label approval remains open.

## Automated release gate

All 18 checks passed: 214 Python tests with 96.36% covered service statements, Java
analyzer verification, platform verification (96 tests), Python lint/format, frontend
tests and coverage, production build, lint/format, Playwright E2E, demo recording,
production dependency audit, Compose validation, corpus evaluation, and Git diff check.

Generated local artifacts are under `dist/`:

- `riskgraph-demo.webm` and its SHA-256 manifest
- `riskgraph-source-2026-09-23.zip` and its verification manifest
- `riskgraph-evidence-2026-09-23.zip`, containing the source archive, runtime results,
  external evaluations, coverage, release logs, corpus metrics, and demo recording

The evidence manifest records `working_tree_dirty: true`. Rebuild it after the final
commit and independent review, then create the `v1.0.0` tag from that exact commit.
