# Release-candidate evidence — 2026-09-13

This bundle records what was actually verified before opening the remediation pull
request. It is not a final-release approval.

## Passed locally

- Complete `tools/dev/verify_release.py` gate: 14/14 commands passed.
- Python: 89 tests passed, 95% combined graph/AI service statement coverage.
- Java analyzer: 39 tests passed.
- Platform API: 34 tests passed.
- Dashboard: 18 tests passed; build, ESLint, Prettier, and production npm audit passed
  with 0 reported vulnerabilities.
- Ruff lint/format, Compose configuration, corpus evaluation, release-package tests,
  and `git diff --check` passed.
- Two pinned external repositories were analyzed twice each with deterministic
  results. Petclinic matched 2/2 endpoint/auth rows; the REST guide matched 0/2 and
  emitted `SPOON_MODEL_FAILED`. Both safely returned REVIEW with zero risk delta.
- The 10-slide presentation passed package integrity, layout, font, native table,
  native chart/workbook, and first-party import checks, then passed slide-by-slide
  visual inspection. SHA-256:
  `d75c60050643438508736f10213004ceac5c5c4e33fc1f981df72870d5535130`.

## Pending external evidence

- The local Docker Desktop engine is not responsive on this host, so the post-fix
  full Compose/DinD validation rerun is delegated to the new Linux CI smoke job.
- A real GitHub pull request and its Check/SARIF artifacts have not yet been recorded
  in this pre-PR snapshot.
- External repository labels remain provisional pending the independent procedure in
  `datasets/external-spring/HUMAN_REVIEW.md`.
- No final tag is authorized until those gates and release approval pass.

## Evidence sources

- `tmp/release-checks/results.json` and logs (local, ignored)
- `datasets/external-spring/observed-results-2026-09-13.json`
- `datasets/regressions/authorization-removal-confirmed.json`
- `docs/security-review-2026-09-13.md`
- `docs/release-rehearsal.md`
- `artifacts/RiskGraph-AI-Final-Presentation-v2.pptx`
