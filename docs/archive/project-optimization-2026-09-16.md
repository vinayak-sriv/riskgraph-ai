# Project optimization record — 2026-09-16

## Outcome

The optimization pass reduced avoidable workspace weight and split the largest
dashboard presentation boundaries without changing the platform contracts,
deterministic analysis, risk policy, or validation behavior.

The tracked repository is approximately 2 MiB. A working checkout is larger only
when it contains reproducible dependencies, build output, coverage, or Git metadata.
Use `tools/release/package.py` for a public source archive; never zip the working
directory directly.

## Changes

- Replaced the 3,643-line dashboard stylesheet with an ordered six-module style
  entry point: foundations, workspace, graph, evidence, forms, and responsive rules.
- Moved the analysis workspace out of the application router shell.
- Moved graph panels and the large-graph summary out of the graph comparison
  controller. State ownership, lazy graph loading, and the rendered behavior remain
  unchanged.
- Removed generated dashboard build/coverage output, test scratch data, TypeScript
  build metadata, and Python bytecode caches after verification.
- Removed a verified 53.4 MiB orphaned temporary Git object. `git fsck --full`
  completed without repository corruption before and after removal.
- Retained ignored dashboard dependencies so the local mentor dashboard remains
  immediately runnable. They are excluded from source archives and Git.

Large tracked files were reviewed rather than rewritten blindly. The npm lockfile,
versioned evaluation corpus, SARIF schema, fixtures, and release presentation are
intentional reproducibility or evidence assets. Removing or hand-minifying them
would reduce credibility and reproducibility, so they remain unchanged.

## Verification

- Prettier and ESLint passed.
- TypeScript production build passed (2,009 modules).
- Vitest passed: 42 tests across 7 files.
- Coverage remained above the configured dashboard gates: 74.62% statements,
  67.26% branches, 74.37% functions, and 77.25% lines.
- Playwright passed all 4 Chromium workflows, including serious accessibility,
  degraded evidence, stale validation, and access-denial behavior.
- The full Python suite passed: 127 tests. The release/cleanup subset passed 13
  focused tests.
- `npm audit --audit-level=high` reported zero vulnerabilities.
- The verified deterministic source archive contains 603 entries and is about
  730 KiB, compared with the former 1.3+ GiB raw working-directory archive.
- `git diff --check` passed.

## Ongoing size discipline

Run the following before packaging or handing the project to another machine:

```powershell
python tools/dev/clean_repo.py --dry-run
python tools/release/package.py --output dist/riskgraph-source.zip --manifest dist/riskgraph-source.manifest.json
```

The cleanup command intentionally identifies generated local data. The release
packager independently uses an explicit allowlist/exclusion policy and verifies the
archive before reporting its digest.
