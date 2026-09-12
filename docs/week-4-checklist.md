# Week 4 Secure Source Acquisition Checklist

Status: **Complete**

## Goal

Replace fixture selection with a reproducible source input: a local Git repository
plus immutable old/new commit SHAs. Week 4 stops at trustworthy acquisition and diff
metadata; Spoon-based semantic extraction begins in Week 5.

## Source Boundary

- [x] Accept an allowlisted local repository path and two full commit SHAs.
- [x] Verify the path is a Git repository and both objects are commits in that repo.
- [x] Reject missing, ambiguous, or invalid revisions with structured errors.
- [x] Never execute repository-provided scripts during acquisition or diffing.
- [x] Materialize Java sources in isolated temporary snapshots and always clean them.

## Deterministic Diff

- [x] Record added, modified, renamed, and deleted Java files.
- [x] Preserve old/new paths and Git change status.
- [x] Produce stable ordering and identical evidence for repeated runs.
- [x] Identify changed line ranges as candidates for semantic extraction.

## Analysis Envelope

- [x] Add a versioned envelope around the unchanged endpoint IR contract.
- [x] Include schema version, analyzer version, repository identity, full old/new SHAs,
  timestamps, diagnostics, and extraction coverage.
- [x] Keep risk separate from confidence/coverage.
- [x] Validate the envelope with JSON Schema and typed models at service boundaries.

## Test Repository

- [x] Generate a minimal Spring sample repository with two commits:
  protected `/admin/export` before, authorization removed after.
- [x] Make commit creation deterministic for CI.
- [x] Test no-change, invalid-SHA, rename, deletion, non-Java-change, and cleanup paths.

## Exit Gate

- [x] One command creates the sample repository and immutable commit pair.
- [x] The Java analyzer returns reproducible diff metadata and provenance.
- [x] CI proves error handling and cleanup on both success and failure.
- [x] Acquisition emits evidence only; deterministic security interpretation remains downstream.

## Mentor-Presentation Upgrade

The next presentation-ready milestone spans Weeks 4–6: select the two sample commits,
show the removed `@PreAuthorize` line, generate before/after IR automatically, and feed
that IR into the already working graph/risk/dashboard path. This is the smallest
upgrade that changes the demo from a fixture simulation into genuine live code analysis.
