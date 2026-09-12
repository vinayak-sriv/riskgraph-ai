# Java Analyzer

Java service for Git diff analysis and Spring Boot annotation extraction.

Architecture constraints:
- Outputs endpoint IR matching `contracts/ir/endpoint-ir.schema.json`.
- Handles annotation-based authorization only for MVP.
- Does not assign risk, call AI, or write to PostgreSQL.

Current state: Weeks 4–6 are complete. `POST /analyze` accepts an allowlisted local
repository root and two full commit SHAs. JGit produces deterministic Java diff
metadata and isolated Java-only snapshots; Spoon extracts changed endpoints,
annotation authorization, service/repository paths, resource sensitivity, and source
locations into `analysis-envelope.schema.json`.

Extraction is method-range scoped and impact-aware for changed service/repository
methods. The evidence envelope preserves multiple paths, policy-backed sensitivity,
qualified method identity, and per-stage confidence. Source acquisition is bounded by
configurable file, byte, and time limits. Target builds and repository code are never
executed. In Compose, each analysis runs in a separate JVM worker process so deadline
cancellation forcibly terminates the parser and its descendants. Native development
defaults to in-process analysis; set `RISKGRAPH_ANALYZER_PROCESS_ISOLATION=true` and
`RISKGRAPH_ANALYZER_EXECUTABLE_JAR` to a packaged analyzer jar to exercise the same
hard-termination boundary.

Run locally:

```powershell
python tools/dev/create_week6_sample.py
$env:RISKGRAPH_ALLOWED_REPOSITORY_ROOTS="$PWD\samples\generated"
mvn -f services/java-analyzer/pom.xml spring-boot:run
```

Use the generated JSON values in `POST http://localhost:8081/analyze`. Full 40-character
SHAs are required. In Docker, set `ANALYZER_REPOSITORY_ROOT` to the host directory to
mount read-only and send its corresponding `/analysis-repositories/...` container path.
Run `mvn -f services/java-analyzer/pom.xml verify` to generate the JaCoCo report under
`services/java-analyzer/target/site/jacoco/`.
