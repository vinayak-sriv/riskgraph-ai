# Developer Tools

Local verification and developer utilities live here, including the foundation and
fixture vertical-slice verification scripts referenced by the root README.

`create_week6_sample.py` deterministically creates the authorization-removal commit
pair. `verify_week6.py` regenerates it, runs the analyzer verification lifecycle, and
validates all JSON contracts.

`create_mvp_samples.py` creates four immutable authored source pairs and native/Compose
manifests. `build_sandboxes.py` verifies authored source before building commit-bound
images; `--prepare-only` leaves those trusted contexts for the isolated Compose image
loader. `start_local.py` manages the explicitly ephemeral native demo.

`verify_release.py` runs the complete unit/contract/build/audit/corpus gate and saves
logs under `tmp/release-checks`. `verify_mvp.py --compose --validation` checks all four
source scenarios, exact extracted IR, repeatability and Docker confirmation against
running Compose services. Its evidence is saved under `tmp/evidence-bundle`.


`python tools/dev/clean_repo.py --dry-run` previews reproducible local artifacts that
can be removed safely. Run it without `--dry-run` to delete dependency trees, build
outputs, generated samples, caches, stale dashboard archives, and runtime files while
preserving source code and Git metadata.

`python tools/release/package.py --output riskgraph-source.zip` builds a deterministic
source archive. It sorts entries, fixes timestamps, and excludes credentials, Git
metadata, dependency trees, build outputs, logs, coverage, and runtime files.
