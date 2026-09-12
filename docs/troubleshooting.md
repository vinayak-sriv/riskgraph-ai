# Troubleshooting

`DEPENDENCY_UNAVAILABLE` / `DEPENDENCY_TIMEOUT`: inspect `tmp/prototype/*.log`; verify
ports 8080–8083 and allow 60 seconds for startup. Errors preserve REVIEW/BLOCK and
are not successful scans. Existing occupied ports are preserved by the launcher.

`INVALID_SERVICE_CREDENTIAL` / `SERVICE_AUTH_NOT_CONFIGURED`: ensure platform,
analyzer, graph, and AI processes share the same non-empty `RISKGRAPH_SERVICE_TOKEN`.
Do not expose internal service ports as a workaround.

`ANALYZER_BUSY`: the bounded worker pool and queue are full. Retry after an active
analysis completes; increase the limits only with matching CPU and memory capacity.

`REPOSITORY_NOT_ALLOWED`: the platform and analyzer must see the same real absolute
path under `RISKGRAPH_ALLOWED_REPOSITORY_ROOTS`. Compose mounts both at
`/analysis-repositories`; native mode defaults to `samples/generated`.

Ollama DEGRADED: `OLLAMA_BASE_URL` must point to the actual instance; run
`curl http://localhost:11434/api/tags` for the default. Set `OLLAMA_MODEL` to an
installed model. In Docker use `host.docker.internal` for the Windows host.
No hosted API key is required. AI failure does not change deterministic risk.

Docker `dockerInference` startup error: this is an independently reported Windows
AF_UNIX runtime-socket failure. See [Docker issue 460](https://github.com/docker/desktop-feedback/issues/460).
Do not factory-reset or unregister Docker WSL distributions to fix a runtime socket.
Quit Docker, retain the transient `%LOCALAPPDATA%\Docker\run` directory as a backup,
and allow Docker to recreate it. The next failure may identify the independent
`%LOCALAPPDATA%\docker-secrets-engine\engine.sock`. After verifying that directory
contains only the transient socket, preserve its parent as a backup too. Clear both
stale runtime paths in the same stopped session: a failed restart can strand the
other socket again. This coordinated repair restored engine 29.7.2 on the verified
Windows host. Some affected hosts still require a Windows restart.
Never delete volumes, containers, WSL disks, or unrelated runtime directories.

Sandbox `DOCKER_UNAVAILABLE_OR_INVALID`: start the engine and run
`python tools/dev/build_sandboxes.py --prepare-only`, then recreate the validation
overlay so its one-shot loader builds the registered images in the dedicated daemon.
`SANDBOX_NOT_REGISTERED` means this repository/commit pair has no approved runtime
adapter. Generic sandbox demonstrations cannot confirm it.

PostgreSQL failure: default profile requires PostgreSQL and Flyway. Native `local`
profile is explicitly ephemeral. An existing pre-Flyway database needs a backup
and operator-reviewed baseline at version 1; do not erase it or auto-baseline it.

Windows locked JAR: the launcher copies packaged JARs into `tmp/prototype` before
starting Java, so Maven can rebuild target artifacts. Stop the recorded prototype
processes before replacing the runtime copies.
