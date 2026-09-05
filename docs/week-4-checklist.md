# Week 4 Secure Source Acquisition Checklist

Status: **Current**

## Goal

Replace fixture selection with a reproducible source input: a local Git repository
plus immutable old/new commit SHAs. Week 4 stops at trustworthy acquisition and diff
metadata; Spoon-based semantic extraction begins in Week 5.

## Source Boundary

- [ ] Accept an allowlisted local repository path and two full commit SHAs.
- [ ] Verify the path is a Git repository and both objects are commits in that repo.
- [ ] Reject missing, ambiguous, or invalid revisions with structured errors.
- [ ] Never execute repository-provided scripts during acquisition or diffing.
- [ ] Materialize revisions in isolated temporary worktrees and always clean them.

## Deterministic Diff

- [ ] Record added, modified, renamed, and deleted Java files.
- [ ] Preserve old/new paths and Git change status.
- [ ] Produce stable ordering and identical output for repeated runs.
- [ ] Identify changed line ranges as candidates for Week 5 semantic extraction.

## Analysis Envelope

- [ ] Add a versioned envelope around the unchanged endpoint IR contract.
- [ ] Include schema version, analyzer version, repository identity, full old/new SHAs,
  timestamps, diagnostics, and extraction coverage.
- [ ] Keep risk separate from confidence/coverage.
- [ ] Validate the envelope with JSON Schema and typed models at service boundaries.

## Test Repository

- [ ] Add or generate a minimal Spring Boot sample repository with two commits:
  protected `/admin/export` before, authorization removed after.
- [ ] Make commit creation deterministic for CI.
- [ ] Test no-change, invalid-SHA, rename, deletion, non-Java-change, and cleanup paths.

## Exit Gate

- [ ] One command submits the sample repository and commit pair.
- [ ] The Java analyzer returns reproducible diff metadata and provenance.
- [ ] CI proves error handling and cleanup on both success and failure.
- [ ] No result is yet described as a vulnerability finding; semantic evidence begins
  with the Week 5 Spoon extractor.

## Mentor-Presentation Upgrade

The next presentation-ready milestone spans Weeks 4–6: select the two sample commits,
show the removed `@PreAuthorize` line, generate before/after IR automatically, and feed
that IR into the already working graph/risk/dashboard path. This is the smallest
upgrade that changes the demo from a fixture simulation into genuine live code analysis.
