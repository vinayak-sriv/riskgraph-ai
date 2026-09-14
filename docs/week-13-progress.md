# Week 13 product-polish progress

Week 13 has started without changing the repository's **Current week: 12** marker.
The marker remains at Week 12 because the independent external-source review gate is
still open.

## Implemented

- The dashboard exposes runtime validation provenance separately from static and AI
  evidence: sandbox revision, source commit, container image ID, probe image ID,
  response SHA-256 and cleanup state.
- The dashboard labels a confirmation as regression-ready only when all current
  immutable provenance fields pass the same shape requirements enforced by the
  platform and regression generator.
- Validation provenance can be copied as structured JSON for review and evidence
  handoff.
- Permanent regression tests now verify the stored confirmation's container image,
  optional legacy probe image, response digest, source commit binding and cleanup
  result before replaying graph and risk assertions.
- The existing graph workspace already provides synchronized before/after views,
  changed/path filtering, node inspection, source-evidence links, fullscreen focus,
  keyboard escape/focus behavior and responsive fallbacks.

## Verification

- Dashboard: 20 tests pass; TypeScript production build, ESLint and Prettier pass.
- Permanent regression slice: 3 tests pass, including the confirmed 22 → 91 risk
  transition and immutable evidence checks.
- PR #18 at `eb714ca377ef1968fdf4815bd46e829d331253d7` is clean and mergeable with PR
  analysis, PR CI and push CI green.

## Gate still open

`datasets/external-spring/manifest.json` remains `label_status: PROVISIONAL`,
`review_type: AI_SOURCE_REVIEW` and `human_reviewed: false`. An independent reviewer
must complete the attribution packet in `datasets/external-spring/HUMAN_REVIEW.md`
and the post-analyzer-0.4.1 evaluation must be rerun before:

1. the Current Week marker advances beyond Week 12;
2. Week 13 is marked complete; or
3. the conditional Python parsing proof of concept begins.
