# Release rehearsal

This is the supported Week 15 rehearsal. Run it from a clean checkout. Do not create
a tag until every exit condition is satisfied.

## Local deterministic gate

```powershell
python tools/dev/verify_release.py
python tools/release/package.py --output dist/riskgraph-source.zip --manifest dist/riskgraph-source.manifest.json
```

The verifier installs the dashboard from `package-lock.json`, runs Python, Java,
frontend, contract, corpus, Compose-model, audit, and diff checks, and retains logs in
`tmp/release-checks`. The packager uses an explicit allowlist policy, fixed timestamps,
sorted paths, post-build verification, and a SHA-256 manifest.

## Container gate

On a working Docker engine:

```powershell
python tools/dev/create_mvp_samples.py
python tools/dev/init_auth.py
docker compose -p riskgraph-release -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app up -d --build --wait
python tools/dev/verify_mvp.py --compose --validation
docker compose -p riskgraph-release -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app down -v --remove-orphans
```

The GitHub `container-smoke` job performs the same full-stack flow on Linux and then
rejects critical vulnerabilities in every built image.

## Required evidence

- All local verifier entries have exit code 0.
- All GitHub PR jobs are green on the exact release commit.
- The four demo scenarios have their expected scores, paths, and verdicts.
- Authorization removal has fresh `CONFIRMED` validation with source commit,
  target image ID, probe image ID, response hash, and cleanup proof.
- Both external Spring cases retain immutable source/license hashes and have complete
  independent human-review attribution.
- The release ZIP manifest hash matches the uploaded artifact.
- The final presentation opens and its charts/tables remain editable.

## Failure recovery

- Preserve `tmp/release-checks`, Compose logs, and the failing GitHub run ID.
- Do not weaken thresholds or convert an error to ALLOW.
- Fix the cause on a new commit and rerun the entire gate.
- If Docker cleanup fails, stop the release and remove only the named
  `riskgraph-release` project after verifying its scope.
