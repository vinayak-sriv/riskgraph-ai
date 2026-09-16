# Release-candidate evidence — 2026-09-15

## Candidate identity

This evidence was generated from an uncommitted audit-remediation working tree based
on commit `4ea99ce0c2a8c0021e760a62500cae5e6e7e7f8b`. It must not be cited as evidence
for that commit or as a final release identity. After the changes are committed, the
same gates must run in CI against the resulting immutable SHA.

## Automated release gate

`python tools/dev/verify_release.py` passed all 14 checks:

| Check | Result |
|---|---|
| Python tests and coverage | 107 passed; 95% total coverage |
| Java analyzer | 40 tests passed |
| Python lint and format | Passed |
| Platform API | 43 tests passed |
| Dashboard install, tests, build, lint and format | 24 tests passed; all other checks passed |
| Production npm audit | 0 vulnerabilities |
| Docker Compose configuration | Passed without requiring a running daemon |
| Provisional synthetic corpus | Passed; test split precision/recall/F1 1.0 within its stated IR-only scope |
| Git diff whitespace check | Passed |

Machine-readable local logs are generated under `tmp/release-checks/`; that ignored
directory is not release evidence until archived by the release owner.

## Four MVP scenarios

`python tools/dev/verify_mvp.py --compose --validation` ran every source-backed
scenario twice through the localhost-only Compose stack and compared deterministic
result fields. The authorization-removal hypothesis was then confirmed by HTTP
probes against the protected and vulnerable images in the dedicated Docker daemon.

| Scenario | Before | After | Delta | New paths | Verdict | Confidence | Validation |
|---|---:|---:|---:|---:|---|---|---|
| Authorization removal | 22 | 91 | +69 | 1 | BLOCK | HIGH | CONFIRMED |
| Safe change | 22 | 22 | 0 | 0 | ALLOW | HIGH | Not required |
| New public sensitive endpoint | 0 | 65 | +65 | 1 | BLOCK | HIGH | Not run |
| Sensitive resource exposure | 0 | 65 | +65 | 1 | BLOCK | HIGH | Not run |

Ollama was unavailable. The three findings reported `DEGRADED`; the safe change did
not invoke AI. This does not weaken the deterministic graph, score, or verdict gate.

## Live React local-mode check

A localhost-only browser check signed in as the existing verification Analyst while
the backend reported GitHub `NOT_CONFIGURED` and `github_connection_required=false`.
The New Analysis and Saved Scans controls remained available. Submitting the pinned
authorization-removal repository and immutable SHAs through the rendered UI produced
the `22 -> 91 BLOCK` source result with HIGH confidence and `CONFIRMED` Docker
validation. The session was signed out and the temporary browser-test credential was
restored. This is automated working-tree evidence, not independent human review.

Detailed observations are recorded in `live-react-local-mode.md`.

## External Spring evaluation

`python tools/evaluation/external_spring.py --compose` verified the pinned source and
license hashes for Spring PetClinic and the Spring `gs-rest-service` guide, then
processed each case twice through the current Compose analyzer:

| Case | Matched endpoint/auth rows | Result | Reason |
|---|---:|---|---|
| spring-petclinic | 2/2 | REVIEW | LOW confidence, incomplete coverage, `AMBIGUOUS_CALL_RESOLUTION` |
| gs-rest-service | 2/2 | REVIEW | LOW confidence, incomplete coverage |

The labels remain `PROVISIONAL / AI_SOURCE_REVIEW`. Vulnerability precision, recall,
F1, and sensitivity accuracy remain unset because an independent human has not yet
recorded attributable decisions in `datasets/external-spring/HUMAN_REVIEW.md`.

## Package

`tools/release/package.py` produced the source archive twice with identical entry
counts and SHA-256 digests. The ignored local outputs are
`dist/riskgraph-source.zip` and `dist/riskgraph-source.manifest.json`. Regenerate
them after the final commit so the archive includes the immutable candidate state.

## Open release gates

- Obtain independent, attributable human review for both external cases.
- Commit the scoped changes and require green CI for the resulting SHA.
- Regenerate and archive the package/evidence bundle from that SHA.
- Obtain explicit release approval before creating any tag.

No commit, push, pull request, Check, comment, release, or tag was created by this
local verification pass.
