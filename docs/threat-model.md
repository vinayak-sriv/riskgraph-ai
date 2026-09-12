# MVP threat model

Target repository source, Git metadata, diffs, and LLM output are untrusted. The
operator controls repository allowlists, local Docker, policy files and service
configuration. This local mentor prototype is not a multi-user internet service.

Java acquisition resolves real paths and immutable SHAs, reads regular Git blobs,
rejects traversal, symlink/LFS Java entries and configured byte/file limits, and
materializes temporary revisions. Source limits are checked before diff/rename
content processing. Target Maven/Gradle hooks and build scripts are never executed
by static analysis. Temporary source directories are cleaned on normal/error paths;
an OS denial during cleanup remains best-effort in the existing analyzer.

Spoon produces annotation/route/call evidence. Complex expressions and ambiguous
resolution carry LOW confidence. Incomplete extraction is REVIEW. The analyzer's
current first-path IR projection for multiple dependencies is explicitly degraded
by the platform. Full framework configuration parsing is outside scope.

The graph uses route-context call nodes to prevent cross-method access fabrication.
Canonical records never include raw source. Risk weights and thresholds are versioned
and validated; LLM output cannot mutate them. An analyzer response must pass schema
and repository/commit identity checks before downstream processing.

Ollama receives redacted evidence only. JSON Schema constrains decoding and Pydantic
checks shape; novel evidence and a test differing from the supplied method/path are
rejected. Generation also pins the permitted HTTP test. Text is always unconfirmed. Service or
model failures preserve deterministic scores and decisions.

Validation accepts only the fixed anonymous GET authorization probe. It uses an
explicit local Docker transport, internal ephemeral network, immutable locally
inspected image IDs, non-root containers, no published ports, read-only roots,
dropped capabilities, no-new-privileges, CPU/memory/PID limits, bounded timeouts,
no redirects, a 64 KiB response cap and cleanup. Only the authored fixture can be
built using the fixed trusted POM. Registered source validation checks repository,
commit and image-label identity. A generic sandbox demonstration is stored
separately and never confirms an arbitrary scanned repository.

The optional validation Compose override starts a dedicated Docker-in-Docker daemon
on an internal network. No application container mounts the host Docker socket and
the daemon's unauthenticated TCP listener is not published to the host or attached to
the application network. The image loader can read only the authored sandbox sources.
Worker interfaces are local operator APIs; do not expose them publicly.
The Spring platform requires cookie sessions, CSRF on all writes, and roles for
scan viewing, analysis/validation and account creation. BCrypt hashes persist in
PostgreSQL through V003; bootstrap secrets live only in ignored local files.
Cookies are HttpOnly and SameSite=Strict, and must be Secure behind HTTPS.
Per-scan ACLs prevent cross-account reads and validation; scan access is explicitly
granted as `VIEW` or `VALIDATE`, with an Admin global override. Account recovery,
disablement, audited review overrides, and project/repository-level organization
membership remain post-MVP work. The prototype remains limited to a trusted host.

Only the Spring platform writes PostgreSQL through transactions and parameterized
SQL. Flyway owns schema evolution. `local` profile explicitly uses bounded ephemeral
storage. DB failure produces a structured failure, not successful persistence.

GitHub workflows build the trusted base's tools and parse the immutable target
revision without executing its scripts. Forks receive no write secrets. Publication
is opt-in and never required for a local demo. SARIF uses stable fingerprints and
only validated relative source locations.
