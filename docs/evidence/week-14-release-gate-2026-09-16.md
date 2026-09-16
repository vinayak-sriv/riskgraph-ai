# Week 14 release-gate evidence — 2026-09-16

## Outcome

`python tools/dev/verify_release.py` passed all 18 stages in 182.88 seconds on
Windows. The run began after repository-generated dependencies, test output, and
build directories were removed with `python tools/dev/clean_repo.py`.

| Stage | Result | Seconds |
|---|---:|---:|
| Python tests and coverage | PASS | 16.12 |
| Java analyzer clean verify | PASS | 34.53 |
| Python lint | PASS | 0.07 |
| Python format | PASS | 0.06 |
| Platform API clean verify | PASS | 35.55 |
| Dashboard locked install | PASS | 19.90 |
| Dashboard unit tests | PASS | 22.05 |
| Dashboard coverage | PASS | 16.34 |
| Dashboard production build | PASS | 4.01 |
| Dashboard lint | PASS | 4.86 |
| Dashboard format | PASS | 1.57 |
| Playwright Chromium install | PASS | 1.78 |
| Dashboard browser acceptance | PASS | 9.27 |
| Deterministic demo recording | PASS | 13.44 |
| Production dependency audit | PASS | 1.55 |
| Docker Compose model validation | PASS | 0.36 |
| Evaluation corpus | PASS | 1.27 |
| Git diff hygiene | PASS | 0.15 |

The browser suite was additionally stress-run for 40 consecutive cases after its
validation-failure case was isolated from an unrelated source-form transition; all
40 passed. CI repeats the gate on the immutable pull-request commit and publishes
the WebM recording plus SHA-256 manifest with 14-day retention.

## Boundary

This proves clean installation, deterministic checks, buildability, local fixture
recording, and Compose configuration. It does not substitute for the independent
human review still required in
`datasets/external-spring/HUMAN_REVIEW.md`, nor does it claim field accuracy from
the synthetic corpus.
