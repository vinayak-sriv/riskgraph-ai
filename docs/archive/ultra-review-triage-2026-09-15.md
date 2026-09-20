# Ultra Review remediation triage — 2026-09-15

## Scope

This note records the repository-side disposition of the September 14 Ultra Review
before remediation begins. The audit reviewed commit
`ca96715454a1b2cd70bd76acbc0bbc128e5714c4`; the production code at current HEAD is
unchanged from that snapshot.

The audit identifiers are prefixed with `UR2-` here because
`docs/security-review-2026-09-13.md` already uses identifiers such as `LOGIC-001`
for different, previously closed findings.

## Batch 1 result

`UR2-TEST-001` is accepted as a regression-coverage gap. The graph-risk suite now
covers two- and three-resource equal-risk cases on one route, every input
permutation, and unequal sensitivity on the same route.

The audit's exact minimum case—two unauthenticated HIGH resources on
`GET /reports`—did not reproduce a crash because both `RawScores` values compare
equal. However, the code still relied on implicit tuple comparison and therefore did
not meet the audit's implementation principle. `UR2-LOGIC-001` and `UR2-LOGIC-002`
are now resolved by a primitive-only `key=` function.

The documented selection policy is highest post-change risk, then largest risk
delta, then stable route ID, then stable resource ID. `RawScores` is deliberately not
made orderable. `TEST-001D` now constructs equal final risk with different baselines,
asserts the largest delta is selected, and verifies identical output across input
permutations. The risk formula, thresholds, IR contract, graph paths, and deterministic
verdict ownership are unchanged.

## Verification required for merge

- Run the complete graph-risk test suite.
- Run the full Python suite with coverage.
- Confirm the four MVP scenario scores and verdicts remain unchanged.
- Review the diff for changes outside this test and triage note.

## Batch 2 result

`UR2-INT-001` and `UR2-TEST-002` are resolved. The `/auth/session` contract now
publishes `github_connection_required` from the same `GithubConnectionGuard` that
enforces source-service access. The dashboard consumes that backend capability
instead of reconstructing policy from GitHub connection status. Missing capability
data fails closed.

An authenticated Analyst can run and read scans when GitHub is explicitly optional
and reports `NOT_CONFIGURED`. Connected deployments still require a linked GitHub
identity, Developers remain unable to mutate scans, signed-out sessions remain
gated, and backend RBAC and scan-level access rules are unchanged.

The OpenAPI session response now documents both the existing `github` object and the
new required capability flag. Backend unit/integration tests and a dashboard test
cover the shared policy contract.

## Batch 3 result

`UR2-SEC-002` is resolved. The AI validation service's unauthenticated `/health`
response is constrained to `status` and `service`; it no longer reads or returns the
configured Ollama base URL or model name. A strict `HealthResponse` model rejects
additional fields, and the checked-in OpenAPI contract describes that exact shape.

The regression test sets recognizable internal provider values and verifies that
neither appears in the response. Detailed provider diagnostics remain internal to
the AI implementation and are not exposed through the public health route.

## Batch 4 result

`UR2-PERF-001` remains a documented MVP scalability boundary rather than an
unbounded workload. The 2,000-row per-revision limit is preserved. Platform tests
now cover 1,999 and 2,000 expanded rows reaching the graph service and 2,001 rows
failing with `ANALYSIS_TOO_LARGE`/HTTP 413 before any graph call, independently for
both the `before` and `after` revisions.

The runtime guide and both synchronous scan API contracts state that the limit is
measured after dependency-path canonicalization. Supporting larger projects remains
deferred until compaction, chunking, or pagination is designed without removing the
resource-exhaustion control.

## Batch 5 result

The automated release-candidate gate is green on the current working tree. All 14
checks in `tools/dev/verify_release.py` pass: the Python, Java analyzer, platform,
dashboard, lint, format, production dependency audit, Compose configuration,
provisional corpus, and diff checks.

The four source-backed MVP scenarios also pass twice deterministically through the
localhost platform stack with exact source IR and HIGH extraction confidence:

- authorization removal: `22 -> 91` (`+69`), one new path, `BLOCK`
- safe change: `22 -> 22` (`0`), no new paths, `ALLOW`
- new public sensitive endpoint: `0 -> 65` (`+65`), one new path, `BLOCK`
- sensitive resource exposure: `0 -> 65` (`+65`), one new path, `BLOCK`

Ollama was unavailable during this run, so the three findings used the deterministic
degraded AI path. AI availability did not change graph facts, risk, or verdicts.

Both pinned external Spring sources and licenses passed integrity verification and
were rerun twice through the current analyzer. Each matched 2/2 expected
endpoint/authentication rows and failed closed to `REVIEW` because coverage is
incomplete. These are still provisional negative labels; no human attribution has
been invented.

The post-change Compose run now passes. The authorization-removal test is
`CONFIRMED` against protected and vulnerable images in the dedicated Docker daemon;
restart persistence, normalized database status, CORS, fail-closed input handling,
offline SARIF, sandbox cleanup, and the Developer/Analyst access matrix also pass.
That live run exposed and corrected a fresh-volume PostgreSQL entrypoint defect, two
stale verifier exception/status assumptions, and stale pull-request example SHAs.

This is not a final release. The working tree is based on commit
`4ea99ce0c2a8c0021e760a62500cae5e6e7e7f8b` but contains the uncommitted audit
remediation, so that SHA is not a final candidate identity. Independent human labels,
a clean final commit with green CI, regenerated evidence, and release approval remain
mandatory before a tag.
