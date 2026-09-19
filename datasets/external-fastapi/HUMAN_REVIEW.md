# External FastAPI human-review packet

This is the same human-review gate `datasets/external-spring/HUMAN_REVIEW.md` used for the
Java/Spring evaluation, ported for Track A batch 6 (docs/pending-updates.md: "pin licensed
public FastAPI repositories and immutable commits; record framework/version, source hashes,
reviewer attribution, coverage, diagnostics, runtime, and deterministic results").

`tools/evaluation/external_fastapi.py --prepare-only` has been run and confirms both cases'
pinned commits, source files, and license file hash-match what's recorded in `manifest.json`.
It has **not** run the full pipeline (that needs the live platform-api/graph-risk/python-analyzer
stack) and this packet does **not** claim that an AI review is a human review.

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
- Expected oracle: 12 unique (method, path) routes across items.py/users.py, all unauthenticated
  per the current heuristic (see limitation below)
- Limitation to verify: this repository exclusively uses FastAPI's `Annotated[Type,
  Depends(...)]` parameter idiom and decorator-level `dependencies=[Depends(...)]`, neither of
  which `extractor.py:_has_depends_param` recognizes (it only checks a parameter's *default*
  value). Every route in both pinned revisions is reported as unauthenticated regardless of its
  real protection (e.g. `read_users`/`create_user`/`update_user` actually require
  `get_current_active_superuser`). This is a real, repo-wide extraction-scope gap -- confirm it
  reads the same way independently before accepting it as an accurate limitation rather than a
  tool defect.

## Case 2 — full-stack-fastapi-template, login.py (+ users.py, same commit)

- Upstream diff: https://github.com/fastapi/full-stack-fastapi-template/commit/11fe2a00ed1300c7545c4935e4b791a71be02e03
- Old commit: `1e08434ca9f5ef0e8e68cd2340e35aba737e1b8c`
- New commit: `11fe2a00ed1300c7545c4935e4b791a71be02e03`
- Expected label: `NEGATIVE`
- Expected oracle: 14 unique (method, path) routes across login.py/users.py, all unauthenticated
  per the current heuristic
- Limitation to verify: same Annotated/decorator-dependency gap as case 1. `users.py` is included
  here (rather than only `login.py`) because the pinned commit's real diff touches it too; the
  analyzer scopes extraction to every changed `.py` file in the commit, not to a hand-picked
  subset, so both files' evidence appears in this case's `source_evidence`.
