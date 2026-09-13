# Runtime and API guide

## Native mode

Prerequisites: Java 21/JAVA_HOME, Maven 3.9+, Python 3.12, Node 22/npm, Git.
Docker is needed for live sandbox validation. From the repository root:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt -r services/graph-risk-service/requirements.txt -r services/ai-validation-service/requirements.txt
npm --prefix apps/dashboard ci
mvn -f services/java-analyzer/pom.xml verify
mvn -f services/platform-api/pom.xml verify
python tools/dev/create_mvp_samples.py
python tools/dev/start_local.py
python tools/dev/wait_ready.py
python tools/dev/verify_mvp.py
```

The launcher selects ephemeral storage. Logs and owned process IDs are under
`tmp/prototype`. `python tools/dev/start_local.py --stop` stops only recorded matching
process identities. Stop native mode before starting Compose on the same ports.
On macOS/Linux use `source .venv/bin/activate`.

## Compose mode

The README command builds five application services and PostgreSQL. A fresh database
receives all Flyway migrations through V006. There is no init SQL mount or automatic baseline.
The default profile runs PostgreSQL only; `app` runs the platform. `sandbox` builds
the independent generic fixture; `future-services` remains a compatibility alias.

Analyzer and platform mount generated repositories read-only at `/analysis-repositories`.
Use `manifest.compose.json` paths in requests. A different host directory requires
`ANALYZER_REPOSITORY_ROOT`; both services must retain identical container paths.
Static analysis never executes target build scripts. Sandbox builds use verified
authored source with a fixed POM. The validation override uses a dedicated private
Docker-in-Docker daemon; application containers never receive the host Docker socket.

```powershell
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml --profile app ps
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml --profile app logs --tail 50
```

## Environment variables

| Variable | Native default / purpose |
|---|---|
| `RISKGRAPH_ALLOWED_REPOSITORY_ROOTS` | `./samples/generated`; OS path-separated real roots; Compose fixes `/analysis-repositories` |
| `RISKGRAPH_SANDBOX_MANIFEST` | `./samples/generated/mvp-v1/manifest.json`; Compose uses mapped manifest |
| `JAVA_ANALYZER_BASE_URL` | `http://localhost:8081` |
| `GRAPH_RISK_BASE_URL` | `http://localhost:8082` |
| `AI_VALIDATION_BASE_URL` | `http://localhost:8083` |
| `DASHBOARD_ORIGIN` | `http://localhost:5173`; sole allowed browser origin |
| `VITE_PLATFORM_API_BASE_URL` | `http://localhost:8080`; frontend build-time setting |
| `OLLAMA_BASE_URL` | `http://localhost:11434`; Compose uses `http://host.docker.internal:11434` |
| `OLLAMA_MODEL` | `llama3.1:8b`; must be installed on selected instance |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/riskgraph`; Compose uses `postgres` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Local development values in `.env.example` |
| `SPRING_PROFILES_ACTIVE` | Omit for PostgreSQL; `local` opts into memory storage |
| `RISKGRAPH_ANALYZER_PROCESS_ISOLATION` | `false` natively; Compose sets `true` so analyzer deadlines kill a separate JVM |
| `RISKGRAPH_ANALYZER_EXECUTABLE_JAR` | Packaged analyzer jar used by isolated worker mode |
| `RISKGRAPH_VALIDATION_DOCKER_HOST` | Dedicated daemon only; the sole accepted configured value is `tcp://validation-docker:2375` |
| `RISKGRAPH_MAX_JAVA_FILES` | 5000 |
| `RISKGRAPH_MAX_JAVA_FILE_BYTES` | 2097152 |
| `RISKGRAPH_MAX_TOTAL_JAVA_BYTES` | 52428800 |
| `RISKGRAPH_MAX_ANALYSIS_SECONDS` | 60 |
| `RISKGRAPH_MAX_CONCURRENT_ANALYSES` | 2; bounded analyzer workers |
| `RISKGRAPH_MAX_QUEUED_ANALYSES` | 4; excess work receives HTTP 429 |
| `RISKGRAPH_SERVICE_TOKEN` | Required shared internal-service secret; native launcher generates one when absent |
| `RISKGRAPH_VALIDATION_CONCURRENCY` | 2; bounds local Docker workers |
| `RISKGRAPH_MAX_DEPENDENCY_RESPONSE_BYTES` | 33554432; bounded response size aligned with the 2,000-row contract |
| `RISKGRAPH_LOGIN_MAX_FAILURES` | 5 failures per username before throttling |
| `RISKGRAPH_LOGIN_IP_MAX_FAILURES` | 50 failures per source IP before throttling username rotation |
| `RISKGRAPH_AI_MAX_CONCURRENCY` | 2 model generations across the AI service process |
| `RISKGRAPH_AI_QUEUE_TIMEOUT_SECONDS` | 5 seconds before returning deterministic degraded output under model saturation |
| `RISKGRAPH_RISK_POLICY` | Optional graph policy path; v1 formula/thresholds must remain exact |
| `RISKGRAPH_SENSITIVITY_POLICY` | Optional analyzer resource-classification policy path |
| `RISKGRAPH_BOOTSTRAP_PASSWORD_FILE` | Read once to create `admin` only when no enabled accounts exist; Compose mounts `tmp/local-auth/admin.password` |
| `RISKGRAPH_SECURE_COOKIE` | `false` for loopback HTTP; `true` for an HTTPS deployment |
| `RISKGRAPH_USERNAME`, `RISKGRAPH_PASSWORD_FILE` | Local verification client; defaults to `admin` and `tmp/local-auth/admin.password` |

Native startup does not load `.env`; set variables in its shell. Compose accepts
`--env-file .env` explicitly. `.env.example` contains container service URLs; for
Compose with host Ollama change its URL to `host.docker.internal`. A Kali VM requires
the actual reachable VM URL. Verify `/api/tags` before integration. No hosted API key
is required. Live model availability is never silently mocked.

## API and local reporting

Platform API 2.0 requires authentication for source scans. The endpoint IR shape
remains compatible, while the source-analysis envelope is version 1.1.0. Use the
dashboard sign-in form or `tools/dev/platform_session.py`.
The client obtains `/auth/csrf`, posts URL-encoded credentials to `/auth/login`, and
fetches a fresh CSRF token. Cookie sessions are HttpOnly, SameSite=Strict, expire
after 30 minutes, and are invalidated on logout/restart. Every POST, including login
and logout, requires `X-CSRF-TOKEN`. CORS allows the single configured dashboard origin.

Developer can retrieve scans. Security Analyst and Admin can analyze and validate.
Admin can list/create accounts through `/admin/users` and the dashboard. Passwords
are BCrypt hashes in PostgreSQL. Existing profile-only users stay disabled under V003.
The local profile keeps accounts in memory, and recreates its bootstrap admin after
restart. Account disable/reset, review overrides and a rules/repository administration
UI remain future product work; no current role can change deterministic scores.

Run `python tools/dev/init_auth.py` before Compose startup. It preserves existing
credentials. Losing the file does not reset a database account; do not delete the
database to recover a password. Missing bootstrap configuration leaves source APIs
locked, with public demo fixtures still available. Compose publishes only the platform
and dashboard ports. Analyzer, graph, and AI ports remain internal and require the
shared service token. Native development binds them to loopback. This remains a
trusted local prototype, not a multi-tenant deployment.

POST `/analyses` accepts only `repository_path`, `old_commit`, `new_commit`; both SHAs
must be complete immutable 40-character IDs. GET `/analyses/{scan_id}` retrieves a
saved result. POST `/analyses/{scan_id}/validation` accepts only a registered source
pair. `/analyses/{scan_id}/sandbox-demonstration/{revision}` records the separate
generic fixture; it cannot confirm the scanned repository.

The unified response preserves IR source evidence, provenance, graph before/after,
added/removed paths, risk/components, confidence/coverage, diagnostics, preliminary
and final verdicts, AI and validation status. Failures have stable codes, FAILED and
REVIEW, and HTTP 400/403/502/503/504. A failed validation preserves an existing BLOCK.
See `contracts/api/platform-api.openapi.yaml` and `docs/decision-policy.md`.

To reproduce a PR event with native services:

```powershell
python tools/reporting/submit_event.py contracts/github/examples/pull-request.json samples/generated/mvp-v1/authorization-removal
```

For Compose, submit the same event with the mapped repository path:

```powershell
python tools/reporting/submit_event.py contracts/github/examples/pull-request.json samples/generated/mvp-v1/authorization-removal --container-repository /analysis-repositories/mvp-v1/authorization-removal
```

Outputs are an offline Check payload, PR summary and SARIF 2.1.0 with stable finding
fingerprints. The vendored unmodified OASIS schema validates SARIF. No GitHub credentials
or GitHub writes are needed locally. The PR workflow parses target revisions with the trusted
base analyzer and never builds the target application. Fork PRs receive read-only
analysis/artifacts. Same-repo publishing requires owner opt-in via repository variable
`RISKGRAPH_PUBLISH=true`; only that job receives Check/comment write permission.

## Quality gate

```powershell
python tools/dev/verify_release.py
python tools/dev/verify_mvp.py --compose --validation
python tools/dev/verify_runtime.py
python tools/dev/verify_access.py
python tools/dev/verify_ollama.py
python tools/evaluation/external_spring.py --compose
```

The first command retains exact logs and results under `tmp/release-checks`. The
second writes `tmp/evidence-bundle` and compares repeated deterministic results.
The runtime verifier restarts only the named Compose platform and confirms database
roundtrip, normalized validation consistency, CORS and probe cleanup. It assumes the
default local database/user names; use it while no other validation is running.

## Isolated validation daemon

Prepare the authored, commit-labeled sandbox contexts, then start the validation
overlay:

```powershell
python tools/dev/build_sandboxes.py --prepare-only
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml --profile app up -d --build
```

The overlay does not mount `/var/run/docker.sock`. It starts a dedicated Docker daemon
reachable only as `tcp://validation-docker:2375` on an internal Compose network. A
one-shot loader builds the two source-bound images and the demonstration image into
that private daemon and pulls the fixed probe image before the validation API starts.
The daemon has its own named image store; deleting that volume is not a routine repair
operation.

## Local Ollama in Compose

The optional overlay pins the official Ollama image by digest, uses two CPUs and
1.5 GiB, binds port 11434 to loopback, and stores model weights in a named volume.
The verified small model is `qwen2.5:0.5b` (397 MB, digest recorded in the evidence
bundle). This tests real schema-constrained integration; explanation quality is not
benchmarked. A larger model needs more memory and should be evaluated separately.

```powershell
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.validation.yml -f infrastructure/docker-compose.ollama.yml --profile app --profile ollama up -d --build
docker compose -p riskgraph-mvp -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.ollama.yml --profile ollama exec -T ollama ollama pull qwen2.5:0.5b
python tools/dev/verify_ollama.py
```

If `OLLAMA_MODEL` is set in your environment, clear it or select an installed model.
The overlay connects the AI worker to `http://ollama:11434`. Without the overlay,
the existing host/VM configuration still applies. API generation is limited to 45
seconds total, two attempts, a 12 KB evidence budget, 64 KiB output, and constrained
generation fields; the complete public Pydantic response contract is always checked.
The generation schema uses tighter string lengths and evidence enums to avoid
llama.cpp grammar expansion failures. See [Ollama structured outputs](https://docs.ollama.com/capabilities/structured-outputs)
and [official Docker setup](https://docs.ollama.com/docker).
