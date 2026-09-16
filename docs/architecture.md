# Architecture

RiskGraph AI is a modular monorepo built around stable contracts. The platform
backend orchestrates scans and owns persistence. Analysis services stay focused
on one deterministic responsibility each, and the AI layer only explains evidence
or proposes validation tests.

## Service Flow

```text
GitHub PR or manual scan
        |
        v
services/platform-api
  orchestration, users, projects, scans, decisions, database writes
        |
        v
services/java-analyzer
  Git diff and Spring annotation extraction, outputs endpoint IR
        |
        v
services/graph-risk-service
  graph construction, graph diff, reachability, deterministic risk scoring
        |
        v
services/ai-validation-service
  Ollama explanation, HTTP test suggestion, Docker-only validation
        |
        v
services/platform-api
  final ALLOW, REVIEW, or BLOCK decision persisted and exposed to dashboard
```

## Analysis pipeline

```mermaid
flowchart LR
  UI[React dashboard] --> P[Spring platform]
  PR[Immutable PR event] --> P
  P --> J[Spoon source analysis]
  J --> IR[Canonical IR + separate evidence]
  IR --> G[NetworkX reachability]
  G --> R[Versioned deterministic risk]
  R --> P
  P --> A[Ollama structured explanation]
  P --> V[Registered Docker sandbox]
  V --> D[Deterministic final decision]
  D --> DB[(PostgreSQL / Flyway)]
  DB --> UI
  P --> Reports[Offline Check / SARIF / summary]
```

## Trust boundaries

```mermaid
flowchart TB
  subgraph Untrusted[Untrusted input]
    Event[Pull request event]
    Repo[Target Java repository]
  end
  subgraph Deterministic[Deterministic security boundary]
    Acquire[Allowlisted immutable checkout]
    Spoon[Spoon extraction]
    IR[Schema-validated IR]
    Graph[Reachability and risk]
    Policy[Decision policy]
  end
  subgraph Advisory[Advisory AI boundary]
    Ollama[Schema-constrained Ollama]
  end
  subgraph Validation[Isolated validation boundary]
    Registry[Registered sandbox manifest]
    DinD[Private Docker daemon]
    Probe[Fixed HTTP probe]
  end

  Event --> Acquire
  Repo --> Acquire
  Acquire --> Spoon --> IR --> Graph --> Policy
  Graph --> Ollama
  Ollama -. hypothesis only .-> Registry
  IR --> Registry --> DinD --> Probe --> Policy
```

Target source is parsed but never built or executed. The AI response cannot create
graph edges, change risk, or confirm a finding. Validation accepts only registered
local images and a fixed structured HTTP authorization probe.

## Local deployment

```mermaid
flowchart LR
  Browser[Browser] -->|session + CSRF| Dashboard[React dashboard]
  Dashboard -->|same public API| Platform[Spring platform API]
  Platform --> Analyzer[Java analyzer]
  Platform --> Risk[Graph and risk service]
  Platform --> AI[AI and validation service]
  Platform --> Postgres[(PostgreSQL)]
  AI --> Ollama[Local Ollama]
  AI -->|named internal network only| Worker[Isolated Docker worker]
  Worker --> Sandbox[Authored sandbox app]
```

The local Docker worker accepts a fixed structured authorization probe, verifies
image/commit identity, and creates an internal network with non-root, read-only,
resource-limited containers. AI never supplies an executable shell command.
The platform implements session authentication, CSRF protection, GitHub connection
checks, login throttling, and Developer, Security Analyst, and Admin authorization.
The default deployment remains a local academic MVP and requires production-specific
TLS, secret management, and operations controls before internet exposure.

- `contracts/` is the source of truth for service communication.
- `platform-api` is the only service that writes to PostgreSQL.
- Analysis services should be stateless where possible.
- `java-analyzer` never scores risk.
- `graph-risk-service` never calls an LLM.
- `ai-validation-service` never assigns risk or confirms findings by itself.
- Validation only targets local Docker sandbox applications.
- `dashboard` talks only to `platform-api`.
- Ollama is external and configured through `OLLAMA_BASE_URL`.

## Why This Shape

The architecture separates deterministic security facts from AI interpretation.
That keeps the MVP explainable, testable, and aligned with the project discipline:
deterministic analysis produces evidence, graph algorithms produce structural
facts, AI interprets and hypothesizes, and validation confirms or rejects.
