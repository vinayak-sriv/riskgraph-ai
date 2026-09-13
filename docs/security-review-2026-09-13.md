# Release-candidate security review — 2026-09-13

## Decision

**MVP engineering candidate: conditionally ready for CI. Production deployment:
not approved. Final v1.0 tag: not approved yet.**

The audit's deterministic correctness and state-integrity findings are fixed with
regression coverage. The remaining blockers are evidence gates: a real pull-request
run, the Linux Docker/DinD smoke job, independent human review of the two external
repository labels, and release approval.

## Closed audit findings

| Finding | Resolution | Verification |
|---|---|---|
| LOGIC-001 | Atomic store mutation preserves concurrent source and sandbox validation | Both completion orders covered in `SourceScanServiceTest` |
| LOGIC-002 | Before/after risk uses the same route/resource identity | Same-route multi-resource regression |
| SEC-002 | Separate account/network rate limits and bounded eviction | Rotation and 10,000-key saturation tests |
| INT-001 | Unique canonical repository record with conflict-safe insert | Flyway V007 plus store tests |
| INT-002 | Local repository identity is derived from canonical repository URI | Sequential commit-pair identity test |
| INT-003 | Synchronous `/scans` now reports HTTP 200 | OpenAPI/runtime contract tests |
| INT-004 | Internal API-key requirements appear in both service OpenAPI documents | Contract assertions for every protected route |
| PERF-001 | Prior finding enrichment is merged before inference | Repeated scan avoids duplicate AI calls |
| PERF-002 | Ollama calls use a provider-wide semaphore and bounded queue | Concurrent overload/degraded-mode test |
| DEP-001/002 | Runtime and validation images use patch-level tags plus immutable digests | Compose validation and CI builds |
| DEP-003 | Pinned Trivy secret/config scan plus built-image dependency/OS scans added to CI | GitHub Actions release and container jobs |
| DEP-004 | Docker CLI/DinD moved to 29.8.0, PostgreSQL to 16.15 Alpine with gosu 1.19 rebuilt on fixed Go 1.25.7, Spring Boot to 4.1.1 with Tomcat 11.0.25, and unused npm/Corepack/Yarn tooling removed from the dashboard runtime | Trivy critical-vulnerability gate on complete runtime images |
| OPS-002 | Main application containers run as UID/GID 10001 | Dockerfile inspection and CI build |
| OPS-003 | CI boots the full Compose + DinD validation stack and runs the four scenarios | `container-smoke` job |
| SEC-001/CLEAN-001 | Deterministic packager independently rejects secrets/traversal and emits SHA-256 manifest | Release packaging tests and artifact job |

## Security boundaries retained

- External repositories are parsed only; their build scripts and application code
  are never executed.
- HTTP validation can address only the shipped local Docker sandbox through the
  dedicated DinD daemon. The host Docker socket is not mounted into the product.
- AI receives structured evidence, cannot choose graph edges or scores, and cannot
  confirm a finding.
- Local offline operation is explicit through
  `RISKGRAPH_REQUIRE_GITHUB_CONNECTION=false`; connected deployments retain the
  secure default `true`.

## Open or deliberately deferred

- **Week 11 evidence:** the remediation PR and its Checks/SARIF artifacts must pass.
- **Week 12 evidence:** two OSS negative cases ran, but the labels remain provisional
  until an independent reviewer completes `datasets/external-spring/HUMAN_REVIEW.md`.
- **Fresh validation provenance:** the historical permanent regression predates
  probe-image ID capture and is marked `LEGACY_MISSING`; CI must produce a fresh run.
- **OPS-001/004/005:** production topology, backup/restore/rollback rehearsal, and
  production observability are outside the local academic MVP and remain prerequisites
  for any production claim.
- **Credential response:** repository history contains no tracked `.env`, but any
  credentials ever distributed in an earlier raw archive must be rotated by their owner.

## Release rule

Create the final tag only when the PR CI jobs are green, the external review manifest
contains complete human attribution, the fresh evidence bundle is archived, and the
release approver signs off. A passing unit suite alone is insufficient.
