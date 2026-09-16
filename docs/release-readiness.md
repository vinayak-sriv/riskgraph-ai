# Release readiness gates

The roadmap is at Week 12. Weeks 8–11 have reproducible local and GitHub evidence;
Week 12 remains open until an independent reviewer records attributable labels.

Latest merged engineering evidence (2026-09-16): all automated release checks pass;
all four source-backed demo scenarios pass with their expected risk deltas and
verdicts; and both pinned external Spring cases pass current-analyzer integrity and
repeatability checks. PR and post-merge CI passed on the exact merged Week 14 commit.
Live isolated-Docker validation includes a confirmed authorization-removal probe and
zero fixed CRITICAL findings across the 12 scanned runtime and sandbox images.
Independent external labels, a clean-machine Week 15 rehearsal, the final
post-review package/evidence bundle, and release approval remain open.

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
- [ ] Reviewed evaluation on 2–4 external Spring Boot repositories with licenses
- [x] Two pinned, licensed external-source regressions with provisional AI source review
- [x] Real PR Check, SARIF, summary, source locations, diagnostics, and stable head identity
- [x] Analyzer 0.4.1 isolates changed conventional source roots in multi-module repositories
- [x] Platform user authentication and Developer/Analyst/Admin authorization on implemented APIs
- [x] MVP validation is deliberately limited to registered authored Docker targets;
      arbitrary reviewed-target adapters are deferred post-MVP
- [ ] Human review of provisional labels and release approval

## Planned after the MVP release

The following requested product extensions are recorded in
[Pending updates](pending-updates.md) and are not blockers for the Java/Spring Boot
academic release:

- framework preflight with an explicit unsupported-framework dashboard/GitHub result;
- Python 3 FastAPI analysis using the existing IR and downstream pipeline;
- an in-app and email notification manager for authorized repository members;
- source-bound validation adapters for separately reviewed target applications;
- later, separately evaluated Django/Flask adapters and browser/mobile push channels.

Current green GitHub evidence for merged Week 14 commit
`f25bad9ff825670cac930908aff0d75de0708846`:

- PR analysis: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/35103157845>
- PR CI: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/35103157897>
- Push CI: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/35103729132>

No project commit, push, PR creation, Check or PR comment publication is performed
by the local verification workflow. Generated sample repositories contain isolated
synthetic test commits; they do not alter the project's Git history.
