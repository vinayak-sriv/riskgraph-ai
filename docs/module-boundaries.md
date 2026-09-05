# Module Boundaries

## services/platform-api

Primary language: Java + Spring Boot.

Responsibilities:
- Own users, projects, pull requests, scans, findings, decisions, and persistence.
- Orchestrate calls to analyzer, graph/risk, and AI/validation services.
- Expose dashboard APIs.
- Write to PostgreSQL.

Must not:
- Parse Java source directly.
- Calculate graph reachability or risk outside the deterministic risk result.
- Call Ollama directly.

## services/java-analyzer

Primary language: Java.

Responsibilities:
- Read old and new commits.
- Analyze Spring Boot annotation-based auth only.
- Extract endpoint IR matching `contracts/ir/endpoint-ir.schema.json`.

Must not:
- Assign risk.
- Call AI.
- Write to PostgreSQL.
- Parse Phase 2 auth mechanisms such as `SecurityFilterChain` unless explicitly
  scaffolded as out of scope.

## services/graph-risk-service

Primary language: Python + FastAPI + NetworkX.

Responsibilities:
- Build security graphs from IR.
- Compare before and after graphs.
- Compute reachability, shortest paths, and risk before/after/delta.

Must not:
- Call AI.
- Write to PostgreSQL.
- Infer endpoint auth from raw source code.

## services/ai-validation-service

Primary language: Python + FastAPI.

Responsibilities:
- Call Ollama through a swappable LLM client.
- Require schema-constrained LLM output.
- Validate LLM output with deterministic schemas/models.
- Generate and run HTTP authorization tests only against Docker sandbox targets.

Must not:
- Assign graph edges or risk scores.
- Treat LLM output as confirmed vulnerability truth.
- Send validation traffic to live, public, or third-party systems.

## apps/dashboard

Primary language: React + TypeScript.

Responsibilities:
- Display scan summaries, risk deltas, graph views, findings, and validation status.
- Read only from `platform-api`.

Must not:
- Call analyzer, graph-risk, AI-validation, or database services directly.
