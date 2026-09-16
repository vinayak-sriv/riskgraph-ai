# Graph and risk service

This FastAPI service builds NetworkX security graphs, compares reachability, applies
the deterministic risk policy, and returns a preliminary verdict.

## API

- `POST /graph/delta`: construct and compare before/after graphs.
- `POST /risk/score`: calculate before, after, and delta scores.
- `POST /analysis`: return graph delta, risk result, and verdict together.
- `GET /health`: service health.

The service consumes validated IR rather than source code. It does not call an LLM,
execute target repositories, perform HTTP validation, or write to PostgreSQL.
Authorization uncertainty, incomplete coverage, and unsupported semantic changes are
kept separate from impact scoring and handled conservatively.

## Verification

From the repository root:

```powershell
python -m pytest tests/graph_risk -q
ruff check services/graph-risk-service tests/graph_risk
```

The risk formula and decision thresholds are documented in
[`docs/risk-scoring.md`](../../docs/risk-scoring.md) and
[`docs/decision-policy.md`](../../docs/decision-policy.md).
