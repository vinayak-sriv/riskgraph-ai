# Development tools

This directory contains deterministic local setup and verification utilities.

## Common commands

| Command | Purpose |
|---|---|
| `python tools/dev/create_mvp_samples.py` | Generate the four immutable authored commit pairs |
| `python tools/dev/init_auth.py` | Create a local bootstrap credential in an ignored file |
| `python tools/dev/build_sandboxes.py --prepare-only` | Verify source bindings and prepare trusted sandbox contexts |
| `python tools/dev/verify_mvp.py --compose --validation` | Exercise the four source scenarios and local validation |
| `python tools/dev/verify_release.py` | Run the aggregate build, test, contract, corpus, and audit gate |
| `python tools/dev/clean_repo.py --dry-run` | Preview removable generated artifacts without deleting them |

Verification output is written only to ignored `tmp/` paths unless a documented
evidence file is explicitly generated. Utilities must not alter the project Git
history, execute target repository code, publish GitHub results, or contact external
validation targets.

The release packager lives at `tools/release/package.py`. It creates a deterministic
source archive while excluding credentials, Git metadata, dependencies, build
outputs, logs, coverage data, and runtime files.
