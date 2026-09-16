# Release-candidate evidence — 2026-09-13

This bundle records what was actually verified before opening the remediation pull
request. It is not a final-release approval.

## Passed locally

- Complete `tools/dev/verify_release.py` gate: 14/14 commands passed.
- Python: 89 tests passed, 95% combined graph/AI service statement coverage.
- Java analyzer: 40 tests passed after adding the multi-module source-root regression.
- Platform API: 34 tests passed.
- Dashboard: 18 tests passed; build, ESLint, Prettier, and production npm audit passed
  with 0 reported vulnerabilities.
- Ruff lint/format, Compose configuration, corpus evaluation, release-package tests,
  and `git diff --check` passed.
- Two pinned external repositories were analyzed twice each with deterministic
  results. Petclinic matched 2/2 endpoint/auth rows; the REST guide matched 0/2 and
  emitted `SPOON_MODEL_FAILED` in the historical 2026-09-13 run. Analyzer 0.4.1
  subsequently matched `/greeting` before and after with no parser diagnostics in a
  pinned direct rerun; dependency coverage remains incomplete, so REVIEW is retained.
- The 10-slide presentation passed package integrity, layout, font, native table,
  native chart/workbook, and first-party import checks, then passed slide-by-slide
  visual inspection. SHA-256:
  `55010bb3963aff4a87816c9aa8359d0090c4882efc0cb93b741b356add566918`.

## Passed GitHub evidence

- PR #18: <https://github.com/vinayak-sriv/riskgraph-ai/pull/18>
- PR analysis passed: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/34805706824>
- PR CI passed, including release packaging, full Compose/DinD scenario exercise,
  and image scans: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/34805706812>
- Push CI passed: <https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/34805703609>
- The PR artifact records schema 1.1.0, analyzer 0.4.1, exact repository/base/head
  identity, `/scans` source locations, diagnostics, 50% dependency coverage, risk
  8 → 8, zero new sensitive paths, and a conservative REVIEW.

## Pending external evidence

- Docker Desktop was not responsive for the post-0.4.1 external-repository platform
  rerun. The pinned direct analyzer rerun and Linux CI are green, but the checked-in
  external platform summary must still be regenerated.
- External repository labels remain provisional pending the independent procedure in
  `datasets/external-spring/HUMAN_REVIEW.md`.
- No final tag is authorized until those gates and release approval pass.

## Evidence sources

- `tmp/release-checks/results.json` and logs (local, ignored)
- `datasets/external-spring/observed-results-2026-09-13.json`
- `datasets/regressions/authorization-removal-confirmed.json`
- `docs/security-review-2026-09-13.md`
- `docs/release-rehearsal.md`
- `artifacts/RiskGraph-AI-Release-Candidate-2026-09-13.pptx`
