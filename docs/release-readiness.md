# Release readiness gates

The roadmap is at Week 12. Weeks 8–11 have reproducible local and GitHub evidence;
Week 12 remains open until an independent reviewer records attributable labels.

Latest release-candidate evidence (2026-09-17): the public setup and all 18 automated
release checks passed from a fresh clone with a new Python environment. All four
source-backed demo scenarios passed with their expected risk deltas and verdicts;
the isolated Docker authorization probe was confirmed; and both pinned external
Spring cases passed source/license integrity and repeatability checks. The nested
validation daemon now loads trusted images from an ignored archive and does not need
registry access. See the
[fresh-clone rehearsal record](evidence/release-rehearsal-2026-09-17.md).
Independent external labels are now recorded: both pinned external Spring cases
(spring-petclinic, gs-rest-service) carry a human-attributed NEGATIVE label,
reviewer Vinayak, dated 2026-09-18 (see
[HUMAN_REVIEW.md](../datasets/external-spring/HUMAN_REVIEW.md)), released as
`v0.x.0`. The final post-review package/evidence bundle remains open.

- [x] Versioned canonical IR and deterministic graph/risk fixture tests
- [x] Source allowlist, immutable commits, Spoon extraction and provenance
- [x] Real-source platform endpoint and responsive dashboard
- [x] Four source scenarios and repeated deterministic comparison verifier
- [x] Ollama schema-constrained provider, validation, redaction, degraded mode tests
- [x] Local Docker-only runner with explicit image/commit binding and cleanup tests
- [x] Deterministic final decision policy
- [x] Offline Check/SARIF/PR summary and opt-in workflow
- [x] Provisional synthetic corpus, integrity checks, group splits, metrics
- [x] PostgreSQL transactional persistence and Flyway migration implementation
- [x] Live Ollama model response verified on the configured instance
- [x] Protected and vulnerable images validated on this host's Docker engine
- [x] Live PostgreSQL migrations and normalized scan/graph persistence
- [x] Restart roundtrip and consistent retained validation across repeat scans
- [x] Complete Compose build/start verified on this host
- [x] Week 7 risk-scope, repository identity and project uniqueness hardening
- [x] Concurrent validation preservation and provider-wide AI backpressure
- [x] Digest-pinned runtime/probe images and non-root application containers
- [x] CI full-stack/DinD smoke and filesystem/container vulnerability scans
- [x] Deterministic release archive verification and SHA-256 manifest
- [x] Confirmed-finding regression generator and permanent 22 → 91 regression
- [x] Permanent regression integrity checks for image, response, commit and cleanup evidence
- [x] Dashboard validation-provenance view with explicit regression-readiness state
- [x] Final presentation structurally validated and visually reviewed
- [x] Public README setup and complete release gate reproduced from a fresh clone
- [x] Full isolated Compose runtime and four-scenario demo reproduced from that clone
- [x] Reviewed evaluation on 2–4 external Spring Boot repositories with licenses
- [x] Two pinned, licensed external-source regressions with human source review
- [x] Real PR Check, SARIF, summary, source locations, diagnostics, and stable head identity
- [x] Analyzer 0.4.1 isolates changed conventional source roots in multi-module repositories
- [x] Platform user authentication and Developer/Analyst/Admin authorization on implemented APIs
- [x] MVP validation is deliberately limited to registered authored Docker targets;
      arbitrary reviewed-target adapters are deferred post-MVP
- [x] Human review of provisional labels and release approval (`v0.x.0`, 2026-09-18)

## Planned after the MVP release

The following requested product extensions are recorded in
[Pending updates](pending-updates.md) and are not blockers for the Java/Spring Boot
academic release:

- framework preflight with an explicit unsupported-framework dashboard/GitHub result;
- Python 3 FastAPI analysis using the existing IR and downstream pipeline;
- an in-app and email notification manager for authorized repository members;
- source-bound validation adapters for separately reviewed target applications;
- later, separately evaluated Django/Flask adapters and browser/mobile push channels.

Current green GitHub evidence for the release-engineering changes and the stabilized
merged commit `4c619eb31fe94e15c8b244a2901df4df6eafa7da`:

- Release handoff PR: <https://github.com/vinayak-sriv/riskgraph-ai/pull/27>
- Test-stabilization PR: <https://github.com/vinayak-sriv/riskgraph-ai/pull/28>
- Final main CI: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/35184173185>

No project commit, push, PR creation, Check or PR comment publication is performed
by the local verification workflow. Generated sample repositories contain isolated
synthetic test commits; they do not alter the project's Git history.
