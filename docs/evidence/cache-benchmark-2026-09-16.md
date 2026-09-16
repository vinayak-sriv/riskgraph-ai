# Analyzer cache evidence — 2026-09-16

## Scope

This evidence covers the immutable commit-snapshot cache and the bounded,
process-local deterministic extraction cache. Target repositories remain input data:
the benchmark uses JGit materialization and Spoon parsing and never invokes Maven,
Gradle, application code, scripts, or binaries from the target repository.

## Reproducible command

From `services/java-analyzer`:

```powershell
mvn -q "-Dtest=AnalysisServiceIntegrationTest#repeatedAnalysisHasStableEvidenceAndDiffs" test
```

The fixture creates two real commits whose small diff removes an annotation from one
Spring endpoint. The first call is cold. The second repeats the exact canonical
repository/commit/config identity. The test asserts equality of changed files,
before/after endpoint evidence, diagnostics, coverage, provenance, and analysis ID.

## Observed result

Local Windows/JDK 21 run:

| Path | Elapsed | Cache evidence |
|---|---:|---|
| Cold small diff | 4,252 ms | 2 snapshot misses; 1 extraction miss |
| Warm identical analysis | 95 ms | 2 snapshot hits; 1 extraction hit |
| Snapshot disk use | 1,058 bytes | Two synthetic Java commit snapshots plus completion markers |
| Dependency fallback count | 0 | The fixture resolved its service/repository path |

The observed warm run was about 44.8× faster for this small synthetic fixture. This
is evidence of the cache path, not a production SLA: JVM, disk, repository size, and
source shape materially affect results.

## Eviction and safety evidence

- `SnapshotCacheTest` verifies exact-key reuse, incomplete-entry rebuilding,
  entry-count eviction, and active-lease protection.
- `DeterministicExtractionCacheTest` verifies immutable copies, LRU eviction, bounded
  entry count, and hit/miss/eviction counters.
- `AnalysisServiceIntegrationTest` verifies that cached output is logically identical
  to a clean extraction. Every response rebuilds its timestamp and repository
  provenance; cached parser/model objects are never reused.
- Snapshot keys include repository identity, full commit SHA, and source-limit
  fingerprint. Extraction keys include both commits, analyzer version, analyzer
  configuration, and sensitivity-policy fingerprint.
- The existing changed-surface analyzer scopes conventional multi-module repositories
  to affected source roots and follows resolved service/repository dependencies. It
  builds the full relevant Spoon source root whenever dependencies must be resolved;
  it does not trade completeness for a narrower unsafe model.
