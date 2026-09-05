# Graph Risk Service

Python/FastAPI service for NetworkX graph construction, graph comparison,
reachability, and deterministic risk scoring.

Architecture constraints:
- Consumes IR contracts, not raw source code.
- Produces graph delta and risk result contracts.
- Does not call AI or write to PostgreSQL.

Implemented mentor scenario:

- `POST /graph/delta` builds deterministic before/after NetworkX graphs and
  compares anonymous-to-sensitive-resource reachability with BFS.
- `POST /risk/score` applies the documented six-factor scoring formula.
- `POST /analysis` returns graph delta, risk result, and deterministic verdict.

The canonical authorization-removal fixture produces `22 -> 91` and `BLOCK`.
