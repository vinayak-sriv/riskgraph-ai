# Release-candidate security review addendum — 2026-09-15

## Decision

**Automated candidate gate: pass. Final v1.0 release: not approved. Production
deployment: not approved.**

The five audit-remediation batches now have regression coverage and a complete green
local quality gate. The deterministic source-to-verdict pipeline passes all four MVP
demo scenarios, including the required before, after, and delta scores. This addendum
does not supersede the remaining human, final-SHA CI, packaging, and approval gates.

## Audit remediation reviewed

- Multi-resource graph scoring uses an explicit primitive-only ranking policy:
  highest post-change risk, largest delta, then stable route/resource identity.
  Equal-risk, unequal-risk, three-way, and different-baseline ties are covered.
- GitHub connection policy is now a backend-published session capability consumed by
  the dashboard, with missing capability data failing closed.
- The unauthenticated AI health response no longer exposes Ollama host or model data
  and is constrained by a strict response schema.
- The 2,000-row per-revision safety limit is tested at 1,999, 2,000, and 2,001 rows
  in both revision directions, documented after dependency-path expansion, and
  represented as HTTP 413.
- The complete 14-check release verifier and all four scenario regressions pass.
- The PostgreSQL image preserves the official entrypoint's root-owned fresh-volume
  initialization and immediate `gosu` privilege drop; forcing `USER postgres` was
  removed after a clean-volume Docker Desktop regression exposed the mismatch.

## Security boundaries confirmed

- Deterministic extraction and graph algorithms own evidence and reachability.
- The transparent policy owns risk and verdicts; AI degradation cannot change them.
- External projects are pinned, hashed, parsed, and never built or executed.
- HTTP authorization validation remains restricted to the local Docker sandbox.
- GitHub-required deployments fail closed, while explicitly offline deployments use
  the backend capability contract rather than a client-side policy guess.

## Evidence and limitations

- Python: 107 tests passed, 95% total coverage.
- Java analyzer: 40 tests passed.
- Platform API: 43 tests passed.
- Dashboard: 24 tests passed; build, lint, and formatting passed.
- Production npm audit: zero reported vulnerabilities.
- Static Compose configuration and deterministic packaging passed.
- Four source scenarios: `22 -> 91 BLOCK`, `22 -> 22 ALLOW`, `0 -> 65 BLOCK`,
  and `0 -> 65 BLOCK`, each repeated deterministically with HIGH confidence.
- Live isolated-Docker authorization validation: `CONFIRMED`; runtime restart,
  normalized persistence, CORS, malformed-input, SARIF, cleanup, and role checks pass.
- Two external Spring cases matched 2/2 expected endpoint/auth rows and returned
  REVIEW because incomplete extraction correctly lowers confidence.

Ollama was unavailable and the AI layer correctly degraded. The working tree was
uncommitted and based on
`4ea99ce0c2a8c0021e760a62500cae5e6e7e7f8b`; this is not a final release SHA.

## Required release actions

1. Have an independent reviewer record attributable external-source labels.
2. Commit the scoped remediation and obtain green CI for the exact commit SHA.
3. Regenerate the deterministic archive and evidence bundle from that SHA.
4. Obtain explicit release approval, then and only then create the final tag.
