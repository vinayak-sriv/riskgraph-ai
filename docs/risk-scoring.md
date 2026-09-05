# Risk Scoring

Risk scoring is deterministic and transparent. The LLM must never assign risk.

```text
Risk = 0.25 * Reachability
     + 0.20 * AuthorizationChange
     + 0.20 * DataSensitivity
     + 0.15 * ExternalExposure
     + 0.10 * PrivilegeImpact
     + 0.10 * Exploitability
```

Categories:
- `0-20 LOW`
- `21-40 MODERATE`
- `41-60 MEDIUM`
- `61-80 HIGH`
- `81-100 CRITICAL`

Every result must report:
- Risk before
- Risk after
- Risk delta
- Category before
- Category after
- Component evidence

## MVP Authorization-Removal Rubric

The six component scores are derived only from IR and graph facts:

- Reachability: `100` when BFS finds an anonymous-to-sensitive-resource path.
- Authorization change: `100` when an authenticated endpoint becomes public.
- Data sensitivity: `LOW=20`, `MODERATE=40`, `MEDIUM=60`, `HIGH=80`,
  `CRITICAL=100`.
- External exposure: `100` when a sensitive endpoint is public.
- Privilege impact: `60` for complete annotation-based authorization removal.
- Exploitability: `50` for a public sensitive `GET`; `20` for a protected
  sensitive `GET`; otherwise `0` in the current scenario.

For the canonical `/admin/export` scenario, these fixed inputs produce risk
`22` before and `91` after. The decision engine returns `BLOCK` when a new
sensitive path exists and risk after is at least `61`, `REVIEW` for risk at
least `41` or a delta of at least `21`, and `ALLOW` otherwise.
