# Verified local evidence bundle

Captured on 9–10 September 2026 from the authored Java/Spring samples.

- `authorization-removal.json`, `safe-change.json`, `new-public-sensitive-endpoint.json`,
  `sensitive-resource-exposure.json`: real source scans with provenance and canonical IR.
- `validated.json`: source-bound vulnerable sandbox confirmation and deterministic BLOCK.
- `docker-validation.json`: protected 403/REJECTED and vulnerable 200/CONFIRMED, image/commit IDs and cleanup.
- `summary.json`, `source-extraction-metrics.json`: four-scenario scores, timing and seven-row extraction check.
- `runtime-checks.json`: PostgreSQL restart/consistency, CORS, malformed input, SARIF and cleanup checks.
- `corpus-evaluation.json`: provisional synthetic IR evaluation; not real-world accuracy.
- `quality-gate.json`: exact exit codes from the full local quality gate.
- `check.json`, `results.sarif`, `summary.md`: offline GitHub output; no publication occurred.

Regenerate with the commands in `docs/runtime-guide.md`. Timing, image IDs and runtime
validation evidence are host-specific. Deterministic source/graph/risk assertions are
checked separately. Full limitations and test counts: `docs/verification-report.md`.
