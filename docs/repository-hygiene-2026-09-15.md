# Repository and submission hygiene inventory — 2026-09-15

## Purpose

This read-only inventory addresses `CLEAN-001` from the Ultra Review. It does not
authorize deletion. It explains why a raw workspace archive can be hundreds of
megabytes while the reproducible source submission is small.

## Observed workspace categories

| Category | Approximate size | Classification |
|---|---:|---|
| `tmp/` | 398 MiB | Ignored generated verification logs, extracted sources, and local runtime state |
| `apps/` | 190 MiB | Primarily ignored dashboard dependencies/build output plus tracked source |
| `services/` | 78 MiB | Primarily ignored Maven build output plus tracked source |
| `samples/` | 69 MiB | Ignored generated Git fixtures and sandbox build context |
| Git object database | 32 MiB | Repository history; not part of the source release archive |
| `dist/` | 0.63 MiB | Ignored deterministic source archive and manifest |

The tracked repository contains approximately 530 files. Exact workspace sizes vary
after tests and builds and are not release invariants.

## Packaging boundary

`tools/release/package.py` uses an explicit exclusion policy for Git metadata,
dependencies, Maven/Gradle output, virtual environments, caches, coverage, logs,
temporary data, generated samples, environment secrets, nested archives, and other
reproducible artifacts. It validates archive paths and deterministic timestamps and
emits a SHA-256 manifest.

For re-audit, submit `dist/riskgraph-source.zip` together with
`dist/riskgraph-source.manifest.json`; do not zip the working directory directly.
The archive must be regenerated after the final commit and its digest archived in
the release evidence.

## Deletion decision

No repository content was deleted during this investigation. The large categories
are ignored and reproducible or are Git history. Local cleanup is optional and must
target only verified generated directories; it is not needed to produce the bounded
source archive.

## Follow-up — 2026-09-16

The authorized cleanup removed the previously inventoried generated Maven output,
temporary verification data, generated samples, caches, and other reproducible
workspace artifacts. The current tracked source is approximately 2 MiB. Dashboard
dependencies are intentionally retained for the local mentor demo, remain ignored,
and are excluded from the release archive.

A later integrity check found one 53.4 MiB orphaned temporary object created by an
interrupted Git maintenance operation. It was removed only after `git fsck --full`
reported no corruption; the follow-up integrity check also passed. Git metadata is
now approximately 33 MiB and remains outside the release archive.

See `project-optimization-2026-09-16.md` for the code-structure changes and current
verification results.
