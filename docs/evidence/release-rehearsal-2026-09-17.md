# Fresh-clone release rehearsal — 2026-09-17

This record covers release-candidate commit
`ae104957c20353cea24f40a4b0bd7f10dc81a0db`. The candidate was cloned from the
public GitHub repository into an empty directory on a Windows host with Docker
Desktop. Python dependencies were installed into a new virtual environment; Maven,
npm, browser, and container checks ran from that clone.

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

`python tools/dev/verify_release.py` passed all 18 checks on the exact candidate:

- Python tests and coverage floor;
- Spoon analyzer and Spring platform Maven verification;
- Python lint and format checks;
- dashboard lockfile install, unit tests, coverage, build, lint, and format checks;
- Chromium installation, four Playwright scenarios, and deterministic demo recording;
- production npm audit, merged Compose configuration, corpus evaluation, and clean diff.

Every entry in `tmp/release-checks/results.json` recorded exit code `0`.

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

- Entries: `584`
- Size: `735,311` bytes
- SHA-256: `a1124569007962b2a986b8303d2b6b54876244786135592e35a85cc73229e657`

The package was generated and independently re-read by
`tools/release/package.py`. Generated environments, dependency trees, build output,
Docker archives, credentials, caches, and temporary evidence were excluded.

## Remaining release gate

An independent reviewer must record attributable decisions for both external cases.
After that review, rerun the package and CI on the final commit, record release
approval, and create the version tag. This document deliberately does not self-sign
those steps.
