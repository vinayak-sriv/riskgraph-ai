# Release readiness gates

The roadmap is at Week 12. Weeks 8–11 have reproducible local and GitHub evidence;
Week 12 remains open until an independent reviewer records attributable labels.

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
- [ ] Source-bound validation adapters for arbitrary reviewed target applications
- [ ] Human review of provisional labels and release approval

Current green GitHub evidence for commit `eb714ca377ef1968fdf4815bd46e829d331253d7`:

- PR analysis: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/34828984795>
- PR CI: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/34828984691>
- Push CI: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/34828980675>

No project commit, push, PR creation, Check or PR comment publication is performed
by the local verification workflow. Generated sample repositories contain isolated
synthetic test commits; they do not alter the project's Git history.
