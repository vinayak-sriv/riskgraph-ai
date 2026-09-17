# Fresh-clone release rehearsal — 2026-09-17

The manual clean-clone rehearsal covered pull-request commit
`0964207df13c7c959e9e23005032a7841c3f61ef`. It was cloned from the public GitHub
repository into an empty directory on a Windows host with Docker Desktop. Python
dependencies were installed into a new virtual environment; Maven, npm, browser,
and container checks ran from that clone. The same source changes were merged by
PR #27, and the later test-only stabilization was merged as
`4c619eb31fe94e15c8b244a2901df4df6eafa7da` by PR #28.

This is reproducibility and release-engineering evidence. It is not an independent
security label review and does not change the provisional status of the external
evaluation cases.

## Clean setup

The public README sequence completed successfully:

1. install `requirements-dev.txt` in an empty virtual environment;
2. generate all four immutable authored Git pairs;
3. create the ignored local administrator credential;
4. verify and build the trusted validation images;
5. verify the pinned source and license hashes for both external Spring projects;
6. start the complete Compose stack and wait for every service to become healthy.

The rehearsal found that a first-run registry lookup from the nested validation
daemon was vulnerable to Docker Desktop DNS failures. The release candidate now
builds an ignored, atomic archive containing the three trusted sandbox images and
the digest-pinned probe image. The isolated loader verifies the whole archive's
SHA-256 checksum before loading it. The service resolves the private preload name
and executes the restored image by its immutable runtime ID.

## Automated release gate

`python tools/dev/verify_release.py` passed all 18 checks in the manual rehearsal:

- Python tests and coverage floor;
- Spoon analyzer and Spring platform Maven verification;
- Python lint and format checks;
- dashboard lockfile install, unit tests, coverage, build, lint, and format checks;
- Chromium installation, four Playwright scenarios, and deterministic demo recording;
- production npm audit, merged Compose configuration, corpus evaluation, and clean diff.

Every entry in `tmp/release-checks/results.json` recorded exit code `0`. GitHub's
clean-checkout CI independently passed verification, deterministic packaging, and
the Linux Docker-in-Docker smoke test for merged commit `4c619eb` in
[run 35184173185](https://github.com/vinayak-sriv/riskgraph-ai/actions/runs/35184173185).

## Live isolated runtime

`python tools/dev/verify_mvp.py --compose --validation` produced:

| Scenario | Risk before | Risk after | New paths | Verdict | Confidence | Validation |
|---|---:|---:|---:|---|---|---|
| Authorization removal | 22 | 91 | 1 | BLOCK | HIGH | CONFIRMED |
| Safe cosmetic change | 22 | 22 | 0 | ALLOW | HIGH | Not required |
| New public sensitive endpoint | 0 | 65 | 1 | BLOCK | HIGH | Not run |
| Sensitive resource exposure | 0 | 65 | 1 | BLOCK | HIGH | Not run |

Ollama was unavailable during this rehearsal, so applicable AI enrichments used the
tested deterministic degraded mode. Risk, reachability, validation, and verdicts do
not depend on the LLM.

The authorization-removal probe ran only in the private Docker validation network,
returned `CONFIRMED`, retained the source and image identities, and completed cleanup.
No external application was executed or tested.

## External Spring repeatability

Both pinned cases were analyzed twice through the Compose stack:

| Case | Endpoint/auth rows | Risk delta | Confidence | Verdict | Diagnostic |
|---|---:|---:|---|---|---|
| Spring Petclinic | 2/2 | 0 | LOW | REVIEW | `AMBIGUOUS_CALL_RESOLUTION` |
| Spring REST Service guide | 2/2 | 0 | LOW | REVIEW | None |

The source and Apache-2.0 license hashes matched the manifest. These negative labels
remain provisional until the independent reviewer completes
`datasets/external-spring/HUMAN_REVIEW.md`.

## Deterministic source package

- Source commit: `4c619eb31fe94e15c8b244a2901df4df6eafa7da`
- Entries: `584`
- Size: `738,458` bytes
- SHA-256: `2585c6e16f2a41ee3b512ec80a2dbae807454d50e0917cb4805327de331cd022`

The package was generated and independently re-read by
`tools/release/package.py`. Generated environments, dependency trees, build output,
Docker archives, credentials, caches, and temporary evidence were excluded.

## Remaining release gate

An independent reviewer must record attributable decisions for both external cases.
After that review, rerun the package and CI on the approval commit, record release
approval, and create the version tag. This document deliberately does not self-sign
those steps.
