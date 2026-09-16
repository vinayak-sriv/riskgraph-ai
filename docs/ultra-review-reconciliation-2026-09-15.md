# Ultra Review finding reconciliation — 2026-09-15

## Review basis

This document reconciles every consolidated finding and adversarial verifier item
against the remediation working tree. The original audit inspected commit
`ca96715454a1b2cd70bd76acbc0bbc128e5714c4`. The current work remains uncommitted and
is based on `4ea99ce0c2a8c0021e760a62500cae5e6e7e7f8b`; a re-auditor must use the final
commit SHA rather than either historical identifier.

The `UR2-` prefix below avoids collision with unrelated IDs in the September 13
security review.

## Complete finding disposition

| Audit ID | Reconciliation status | Evidence or explicit disposition |
|---|---|---|
| UR2-LOGIC-001 | Resolved | Risk candidates use a primitive-only `key=` rank; no `RawScores` ordering is possible |
| UR2-OPS-001 | Open, outside local MVP | No production topology is claimed; Kubernetes remains explicitly outside MVP scope |
| UR2-OPS-002 | Open, production prerequisite | No backup/restore/rollback capability is claimed without a production data topology |
| UR2-INT-001 | Resolved | Backend session publishes the GitHub-required capability; React consumes it and missing data fails closed |
| UR2-SEC-001 | Open, conditional on production topology | Current single-node local rate limiting is retained; distributed/proxy policy awaits OPS-001 |
| UR2-PERF-001 | Mitigated limitation, not removed | The 2,000-row DoS bound is documented and tested at 1,999/2,000/2,001 in both revisions |
| UR2-PERF-002 | Verified existing control; not an adverse finding | Analyzer workers and queue are bounded and fail with `ANALYZER_BUSY`; production capacity metrics remain part of OPS-003 |
| UR2-PERF-003 | Verified existing control; not an adverse finding | Provider-wide AI concurrency/backpressure and deterministic degraded behavior remain tested; the audit explicitly called the semaphore correct |
| UR2-ARCH-001 | Deferred by audit recommendation | `SourceScanService` decomposition waits for stable APIs and a separately approved architecture change |
| UR2-ARCH-002 | Deferred by audit recommendation | `/analyses` and `/scans` remain compatible public surfaces pending a versioned deprecation plan |
| UR2-OPS-003 | Open, production prerequisite | Local logs/health remain; production metrics, tracing, alerting and SLOs are not claimed |
| UR2-LOGIC-002 | Resolved | Tie policy is highest after-risk, largest delta, stable route ID, stable resource ID |
| UR2-INT-002 | Deferred by audit recommendation | Persisted execution/verdict status and evidence-quality status await an additive migration plan |
| UR2-TEST-001 | Resolved | Two-way, three-way, unequal-sensitivity, permutation, and different-baseline candidate tests exist |
| UR2-TEST-002 | Resolved | React integration covers optional, mandatory, connected, omitted-capability and role behavior |
| UR2-SEC-002 | Resolved | Public AI health response is strict `status`/`service` only; topology values are regression-tested absent |
| UR2-CLEAN-001 | Investigation completed; no deletion | Workspace size is attributable to ignored reproducible artifacts and Git history; use the deterministic source package |

The open/deferred rows are not represented as fixes. They are the same production or
high-risk architecture items that the audit explicitly said should not block an
academic demo and should not be implemented before topology, migration, or API-policy
decisions exist.

The post-reconciliation live Compose check also found and corrected a clean-volume
startup defect outside the original finding list: the custom PostgreSQL Dockerfile
forced `USER postgres`, preventing the upstream entrypoint from owning a new volume
before dropping privileges through the rebuilt `gosu`. A static container-contract
test now preserves the required initialization/privilege-drop sequence.

## Additional verification defects found

These changes are outside the original finding IDs but are directly attributable to
the audit's required Docker, diff-review, and final-QA steps. They are disclosed here
so a re-auditor does not have to infer why those files changed.

| Local QA ID | Defect | Resolution and evidence |
|---|---|---|
| RC-QA-001 | Fresh PostgreSQL volume could not initialize on Docker Desktop | Preserve the official entrypoint's initial root phase; live Compose database is healthy and a Dockerfile contract test prevents `USER` regression |
| RC-QA-002 | Runtime/access verifiers expected raw `HTTPError` and pre-ownership status codes | Assert the shared verification client's normalized `RuntimeError` contract and the current fail-closed 403 ownership policy; both live verifiers pass |
| RC-QA-003 | Checked-in PR example referenced obsolete generated commits | Align example base/head SHAs to the current deterministic authorization-removal manifest and regression-test the relationship |
| RC-QA-004 | Sandbox cleanup check queried host Docker instead of the dedicated daemon | Query labeled containers and networks inside `validation-docker`; the corrected live check reports complete cleanup |

No production topology, distributed throttling, API consolidation, database status
migration, large-project expansion strategy, or orchestration refactor was introduced
under these QA corrections.

## Adversarial verifier checklist

| Verifier requirement | Status |
|---|---|
| TEST-001A equal-risk resources | Covered with two resources and both permutations |
| TEST-001B three-way tie | Covered with all six permutations |
| TEST-001C unequal sensitivity | Covered; CRITICAL supplies the summary vector |
| TEST-001D equal final risk/different delta | Covered; largest delta is selected deterministically |
| TEST-002A explicit local mode | Passed in component tests and a live localhost React flow using an authenticated Analyst |
| Connected GitHub mode | Covered; disconnected sessions remain gated when capability is required |
| Backend RBAC | Covered; Developer remains read-only and Analyst/Admin retain mutation roles |
| AI decision isolation | Preserved; AI cannot modify paths, scores, confidence or verdict |
| Docker validation isolation | Passed live on the working tree; authorization removal is runtime-confirmed in the dedicated daemon |
| Four MVP scenarios | Passed twice through the local source pipeline with exact scores and HIGH confidence |
| Full local suites | 107 Python, 40 analyzer, 43 platform, and 24 dashboard tests pass; all 14 aggregate checks pass |
| External Spring evidence | Current analyzer rerun checked in; labels remain explicitly provisional |
| Human labels | Open; only an independent named reviewer may complete them |
| Final evidence SHA | Open until changes are committed and CI runs against the immutable SHA |

The live React flow used GitHub `NOT_CONFIGURED` with
`github_connection_required=false`, submitted the pinned authorization-removal
repository and immutable commit pair, rendered `22 -> 91 BLOCK` with HIGH confidence
and `CONFIRMED` Docker evidence, and exposed the result under Saved Scans. The local
session was signed out and its temporary credential was restored afterward.

## Rejected attack claims preserved

The remediation does not weaken the controls behind rejected findings R1–R7: CSRF
remains enabled on the platform, authorization remains server-side, target projects
are parsed but never built, validation retains the dedicated local Docker boundary,
AI does not own deterministic decisions, throttling maps remain bounded, BCrypt cost
remains unchanged, and no production-readiness claim is made.

## Re-audit submission set

Submit the following after the final commit and green CI:

1. The immutable commit SHA and CI run links.
2. `dist/riskgraph-source.zip` and its SHA-256 manifest, regenerated from that SHA.
3. This reconciliation, the September 15 security review, and the release-candidate
   root README evidence-bundle index.
4. The current external Spring observation and, if available, independently
   attributable human review entries.
5. Fresh Docker-only validation evidence for the final SHA (the working-tree rerun
   already passes and should be repeated after commit).

Do not submit a raw workspace ZIP: it includes ignored dependencies, build outputs,
generated fixtures, runtime logs, and Git history that are deliberately excluded by
the deterministic packager.
