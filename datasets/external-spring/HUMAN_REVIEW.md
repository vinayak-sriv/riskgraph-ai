# External Spring human-review packet

This packet is the remaining Week 12 approval step. The automated run on
2026-09-13 verified both upstream repositories, immutable commits, source hashes,
license hashes, repeatability, and fail-closed REVIEW decisions. It did **not**
claim that an AI review is a human review.

## Reviewer procedure

For each case below, independently inspect the linked upstream diff and both pinned
source revisions. Confirm whether the expected endpoint/authentication oracle is
correct, whether the change is security-positive or security-negative within the
annotation-only MVP scope, and whether the noted extraction limitation is accurate.
Do not infer runtime accessibility from the absence of an annotation.

After review, add a `review` object to each case in `manifest.json` with:

```json
{
  "reviewer": "name or stable reviewer identifier",
  "reviewed_at": "YYYY-MM-DD",
  "label": "NEGATIVE",
  "notes": "Independent source-based justification"
}
```

Only after both cases are reviewed should the manifest be changed to
`label_status: REVIEWED`, `review_type: HUMAN_SOURCE_REVIEW`, and
`human_reviewed: true`. Automated validation rejects incomplete attribution.

## Case 1 — Spring Petclinic

- Upstream diff: https://github.com/spring-projects/spring-petclinic/commit/bb37aad8c332264723817d855e8b3b96b7c392bc
- Old commit: `0f6e8614047bd74cf6223b4d8a858d2ed2824f8a`
- New commit: `bb37aad8c332264723817d855e8b3b96b7c392bc`
- Expected label: `NEGATIVE`
- Expected oracle: GET `/owners`, no authorization annotation in either revision
- Automated result: 2/2 expected endpoint/auth rows, zero risk delta, REVIEW
- Limitation to verify: ambiguous helper call prevents deterministic resource resolution

Reviewer decision: _pending_

## Case 2 — Spring REST Service guide

- Upstream diff: https://github.com/spring-guides/gs-rest-service/commit/389429a7345a718c27a77cabc495cf7ea68bbaee
- Old commit: `ccccdcd71c068276d2fc5ccf1e155e26a5614a7c`
- New commit: `389429a7345a718c27a77cabc495cf7ea68bbaee`
- Expected label: `NEGATIVE`
- Expected oracle: GET `/greeting`, no authorization annotation in either revision
- Automated result: 0/2 expected rows, zero risk delta, REVIEW
- Limitation to verify: duplicate application classes across guide modules cause `SPOON_MODEL_FAILED`

Reviewer decision: _pending_
