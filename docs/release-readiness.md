# Release readiness gates

The roadmap CURRENT WEEK marker is unchanged. Implemented future-stage work does
not establish that every weekly quality gate has passed.

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
- [ ] Reviewed evaluation on 2–4 external Spring Boot repositories with licenses
- [x] Two pinned, licensed external-source regressions with provisional AI source review
- [x] Platform user authentication and Developer/Analyst/Admin authorization on implemented APIs
- [ ] Source-bound validation adapters for arbitrary reviewed target applications
- [ ] Human review of provisional labels and release approval

No project commit, push, PR creation, Check or PR comment publication is performed
by the local verification workflow. Generated sample repositories contain isolated
synthetic test commits; they do not alter the project's Git history.
