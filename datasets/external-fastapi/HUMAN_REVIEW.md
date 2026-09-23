# External FastAPI human-review packet

> The current analyzer was rerun through the live Compose stack on 2026-09-23.
> Both pinned cases matched every reviewed endpoint/authentication oracle row and
> remained fail-closed at `REVIEW`. Independent label approval is still pending.

This is the same human-review gate `datasets/external-spring/HUMAN_REVIEW.md` used for the
Java/Spring evaluation, ported for Track A batch 6 (docs/pending-updates.md: "pin licensed
public FastAPI repositories and immutable commits; record framework/version, source hashes,
reviewer attribution, coverage, diagnostics, runtime, and deterministic results").

`tools/evaluation/external_fastapi.py --compose` has been run and confirms both cases'
pinned commits, source files, license hashes, deterministic repeated results, and endpoint/auth
oracles through the live platform, graph, and Python analyzer stack. This packet does **not**
claim that an AI review is a human review.

## Reviewer procedure

For each case below, independently inspect the linked upstream diff and both pinned source
revisions. Confirm whether the expected endpoint/authentication oracle is correct, whether the
change is security-positive or security-negative within the annotation-only MVP scope, and
whether the noted `known_limitation` is accurate. Do not infer runtime accessibility from the
absence of a detected authentication dependency -- see the limitation note on each case.

After review, add a `review` object to each case in `manifest.json` with:

```json
{
  "reviewer": "name or stable reviewer identifier",
  "reviewed_at": "YYYY-MM-DD",
  "label": "NEGATIVE",
  "notes": "Independent source-based justification"
}
```

Only after both cases are reviewed should the manifest be changed to `label_status: REVIEWED`,
`review_type: HUMAN_SOURCE_REVIEW`, and `human_reviewed: true`. Automated validation
(`read_manifest()`) rejects incomplete attribution.

Before human review, run the full pipeline once to produce the observed-results snapshot this
packet references (needs a running platform-api with `PYTHON_ANALYZER_BASE_URL` configured):

```
python tools/evaluation/external_fastapi.py
```

This writes `tmp/external-fastapi-evaluation/summary.json` and one JSON file per case. Copy
`summary.json` to `datasets/external-fastapi/observed-results-<date>.json` once it looks right,
matching the existing `datasets/external-spring/observed-results-*.json` convention.

## Case 1 — full-stack-fastapi-template, items.py + users.py

- Upstream diff: https://github.com/fastapi/full-stack-fastapi-template/commit/e13d120fd1e09fe7383223fbec90076ed2ae4e87
- Old commit: `afc55bdf910ad53f80a00c105e3e7370b9be6323`
- New commit: `e13d120fd1e09fe7383223fbec90076ed2ae4e87`
- Expected label: `NEGATIVE`
- Expected oracle: 12 unique (method, path) routes across items.py/users.py; 11 have
  deterministic authentication evidence and `POST /open` is intentionally public.
- Limitation to verify: `CurrentUser` aliases and decorator-level superuser dependencies are
  recognized, but the analyzer does not infer a role hierarchy from dependency bodies. Dynamic
  router prefixes and type-based method dispatch remain outside the deterministic subset.

## Case 2 — full-stack-fastapi-template, login.py (+ users.py, same commit)

- Upstream diff: https://github.com/fastapi/full-stack-fastapi-template/commit/11fe2a00ed1300c7545c4935e4b791a71be02e03
- Old commit: `1e08434ca9f5ef0e8e68cd2340e35aba737e1b8c`
- New commit: `11fe2a00ed1300c7545c4935e4b791a71be02e03`
- Expected label: `NEGATIVE`
- Expected oracle: 14 unique (method, path) routes across login.py/users.py; login,
  password-recovery/reset, and `POST /open` routes are public, while `test-token`, the
  recovery HTML route, and protected user-management routes have authentication evidence.
- Limitation to verify: roles remain unknown even for the superuser dependency. `users.py` is
  included because the pinned commit's real diff touches it too; extraction covers every
  changed `.py` file in the commit rather than a hand-picked subset.
