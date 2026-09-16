# Week 14 reliability and write-up progress

The Week 14 engineering deliverables are complete. The roadmap marker remains at
Week 12 because independent human review of the external Spring labels is still open.
That governance gate also prevents Week 13 from being declared fully complete and
continues to block the conditional Python parser proof of concept.

## Completed engineering work

- The local release verifier now enforces the 93% Python coverage floor, dashboard
  coverage, browser tests, and reproducible demo recording in addition to the Java,
  Python, React, Compose-model, corpus, audit, and diff checks.
- CI generates a bounded-retention WebM walkthrough and SHA-256 manifest from local
  fixtures without contacting any live target.
- The architecture guide now documents the analysis pipeline, trust boundaries, and
  local deployment boundary and reflects implemented platform authorization.
- [Technical report draft](report-draft.md) records the method, implementation,
  evaluation, limitations, and reproducibility references without presenting
  provisional synthetic results as field accuracy.
- [Demo recording guide](demo-recording.md) provides a repeatable command and labels
  the recording as product evidence rather than independent evaluation.
- The external-review packet now matches analyzer 0.4.1 results while preserving the
  pending human decisions.

## Clean-install and recovery gate

The 2026-09-16 rehearsal passed all 18 verifier stages in 182.88 seconds. The
recorded result is preserved in
[the Week 14 release-gate evidence](evidence/week-14-release-gate-2026-09-16.md).

Run from a clean checkout:

```powershell
python tools/dev/clean_repo.py
python tools/dev/verify_release.py
python tools/release/package.py --output dist/riskgraph-source.zip --manifest dist/riskgraph-source.manifest.json
```

The release verifier writes one log per check and `tmp/release-checks/results.json`.
On failure it exits non-zero, retains the logs, and does not publish, tag, weaken a
threshold, or change an analysis result. Recovery requires fixing the cause and
rerunning the whole gate. Docker cleanup remains scoped to the named Compose project.

## Remaining external action

An independent reviewer must complete both decisions in
`datasets/external-spring/HUMAN_REVIEW.md`. Until then, Week 13 and Week 14 are
engineering-complete but gate-limited, and the project must not claim reviewed
real-world precision, recall, or F1.
