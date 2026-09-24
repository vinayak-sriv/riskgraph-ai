# RiskGraph AI — Project Context for Codex

> Place this file at the repo root as `AGENTS.md`. Read it fully before generating or
> modifying any code. If a request conflicts with the scope boundaries in Section 6,
> flag the conflict instead of silently expanding scope.

---

## 1. One-paragraph pitch

RiskGraph AI is a security-analysis platform for Java + Spring Boot applications.
For every pull request, it builds a **security graph** of the application (users,
roles, endpoints, services, repositories, databases, sensitive data) for the code
**before** and **after** the change, compares the two graphs, and detects whether the
change introduced a new path from an untrusted entry point (e.g. an anonymous user)
to a sensitive resource (e.g. a customer database). It scores the risk delta,
uses an LLM to explain the evidence and propose a validation test, runs that test
against a sandboxed Docker copy of the app, and returns one of three verdicts:
**ALLOW / REVIEW / BLOCK**.

Core discipline — do not violate this while writing code:
**Deterministic analysis produces evidence → graph algorithms produce structural
facts → AI only interprets/explains/hypothesizes → validation confirms or rejects.**
The AI layer must never be the source of truth for whether something is vulnerable.

---

## 2. Pipeline architecture (in order)

```
GitHub PR (old commit + new commit)
        │
        ▼
Stage 1  Git/Diff Analyzer            → identifies changed files/methods
        │
        ▼
Stage 2  Static Analyzer (Java)       → AST parse, extract endpoints, auth
        │                                requirements, call graph, DB access
        ▼
Stage 3  Intermediate Representation  → normalized JSON (see Section 4 — this
        │                                is the contract between all modules)
        ▼
Stage 4  Security Graph Construction  → build G_before and G_after (NetworkX)
        │
        ▼
Stage 5  Graph Comparison + Reachability → BFS/DFS: can entry point X reach
        │                                    sensitive node Y? before vs after
        ▼
Stage 6  Risk Scoring Engine          → transparent weighted formula (Section 5)
        │
        ▼
Stage 7  AI Reasoning Layer           → LLM receives structured evidence JSON,
        │                                returns explanation + hypothesis
        ▼
Stage 8  Security Test Generator      → LLM proposes an HTTP-level test
        │
        ▼
Stage 9  Validation Engine            → runs test against Dockerized test app
        │                                ONLY — never a live/public system
        ▼
Stage 10 Decision Engine              → ALLOW / REVIEW / BLOCK
        │
        ▼
Dashboard (React) + GitHub Check/PR comment
```

---

## 3. Tech stack (do not substitute without asking)

| Layer | Technology | Notes |
|---|---|---|
| Main platform backend | **Java + Spring Boot** | users, auth, APIs, GitHub integration, orchestration |
| Analysis service | **Python + FastAPI** | static-analysis support, graph engine, risk engine, AI integration |
| Static analysis (Java target) | **JavaParser** or **Spoon** | prefer Spoon if full call-graph/type resolution is needed |
| Graph engine | **NetworkX** | BFS/DFS, reachability, shortest path |
| Frontend | **React + TypeScript** | dashboard, graph visualization, before/after comparison |
| Database | **PostgreSQL** | see Section 7 for schema |
| Containerization | **Docker Compose** | backend, AI service, DB, and the sandboxed test application |
| CI/CD | **GitHub Actions** | triggers RiskGraph analysis on `pull_request` events |

Kubernetes, multi-language parsing, and taint/data-flow tracking are explicitly
**out of MVP scope** — see Section 6.

---

## 4. The Intermediate Representation (IR) — the contract Codex must respect

Every module downstream of Stage 2 consumes **only** this JSON shape, never raw
source code. This is what keeps the graph/risk/AI layers language-agnostic and lets
four people build in parallel against a shared contract. Do not change field names
without updating this file and notifying all modules.

```json
{
  "endpoint": "/customers",
  "method": "GET",
  "controller": "CustomerController",
  "authentication": true,
  "required_role": "ADMIN",
  "service": "CustomerService",
  "repository": "CustomerRepository",
  "resource": "Customer",
  "sensitivity": "HIGH"
}
```

**Graph node types (enum, do not add without updating this file):**
`USER, ROLE, ENDPOINT, CONTROLLER, FUNCTION, SERVICE, DATABASE, DATA_RESOURCE, EXTERNAL_SERVICE`

**Graph edge types (enum):**
`CAN_ACCESS, CALLS, READS, WRITES, REQUIRES_ROLE, CONNECTS_TO, RETURNS, HAS_ROLE`

**AI evidence-in / output schema (Stage 7 — enforce this shape, reject free-text-only
LLM output):**

```json
{
  "finding": "Authorization Bypass",
  "evidence": ["ADMIN authorization removed", "customer database newly reachable"],
  "hypothesis": "Unauthenticated customer access",
  "recommended_test": "GET /customers without authentication",
  "confidence": "HIGH"
}
```

**Platform scan-result extension (version 1.2.0):** findings include deterministic
`risk_result` and `handler_refs`; the top-level risk result includes `finding_results`
and authoritative policy metadata (weights, thresholds, and category bands). These are
additive platform-result fields and do not change the Stage 3 endpoint IR above.

---

## 5. Risk scoring formula (transparent, not arbitrary — do not let the LLM assign this)

```
Risk = 0.25 × Reachability
     + 0.20 × AuthorizationChange
     + 0.20 × DataSensitivity
     + 0.15 × ExternalExposure
     + 0.10 × PrivilegeImpact
     + 0.10 × Exploitability
```

Categories: `0–20 LOW · 21–40 MODERATE · 41–60 MEDIUM · 61–80 HIGH · 81–100 CRITICAL`

Always report **Risk Before, Risk After, and Risk Delta** — never just an absolute score.

---

## 6. MVP scope — hard boundaries

**In scope for MVP:**
- One framework: Spring Boot, annotation-based auth (`@PreAuthorize` etc.) only
- Three change types: authorization removal, new public endpoint, sensitive resource exposure
- Basic graph (Role, Endpoint, Service, Repository, Database) + BFS reachability
- Transparent weighted risk scoring (Section 5)
- AI explanation from structured evidence (never raw code → LLM)
- One validation mechanism: HTTP authorization test against a Docker sandbox
- Dashboard: before/after graph + risk delta

**Explicitly NOT in MVP — do not build unless the roadmap in Section 8 says the
checkpoint was reached:**
- Multi-language support (a second language is a conditional post-Week-12 stretch goal only, see Section 8)
- Outbound email, browser/mobile push, SMS, and messaging notifications (post-MVP; see Section 8)
- `HttpSecurity`/`SecurityFilterChain`-style auth config parsing (annotation-based only for now)
- IDOR analysis, privilege-escalation graphs, data-flow/taint tracking (Phase 2)
- Kubernetes, GraphQL, dependency/library graph edges
- Historical learning, repo-specific policy DSL (Advanced features, post-MVP)
- Autonomous testing against any system that is not a local Docker sandbox — **never**
  generate or run a test against a live/public/third-party system

If a prompt asks for something in the "NOT in MVP" list, implement the smallest
scaffold needed and clearly comment `// PHASE 2 — out of current scope` rather than
building it out fully.

---

## 7. Database schema (PostgreSQL — evolve carefully, keep migrations)

```
USERS(id, name, role)
PROJECTS(id, repository, framework)
PULL_REQUESTS(id, project_id, old_commit, new_commit)
SCANS(id, pull_request_id, status, risk_before, risk_after)
GRAPH_NODES(id, scan_id, node_type, name)
GRAPH_EDGES(id, source_id, target_id, relationship)
FINDINGS(id, scan_id, type, severity, description)
AI_ANALYSIS(id, finding_id, hypothesis, explanation)
VALIDATION_TESTS(id, finding_id, test, expected_result, actual_result, status)
```

RiskGraph platform roles: `Developer` (view only), `Security Analyst` (review,
override, trigger validation), `Admin` (configure rules, manage users/repos).

---

## 8. Weekly roadmap (16 weeks → first week of December)

Codex should treat this as the current source of truth for "what phase are we in."
Update the **CURRENT WEEK** marker below each week during your mentor check-in.

> **CURRENT WEEK: 12**  ← Attributable human source review is recorded; independent release sign-off is pending

| Wk | Focus | Deliverable |
|---|---|---|
| 1 | Architecture foundation | Repository boundaries, Docker Compose skeleton, threat model, ADRs, and ownership rules |
| 2 | Contracts and CI | Versioned IR/API contracts, DB migrations, golden fixtures, schema validation, and baseline CI |
| 3 | Fixture vertical slice | Fixture → graph → BFS → risk delta → deterministic verdict → dashboard for authorization removal |
| 4 | Secure source acquisition | Analyze a local repository at two real commit SHAs, isolate revisions, compute a deterministic diff, and record provenance |
| 5 | Spring endpoint/auth extraction | Spoon-based extraction of routes, HTTP methods, controllers, and annotation-based authorization with source locations |
| 6 | Call/resource extraction | Resolve controller → service → repository → resource paths and emit validated IR for the changed surface |
| 7 | Graph/risk hardening | Stable node identities, evidence provenance, reachability/risk tests, confidence/coverage reporting, and score calibration |
| 8 | Real-code mentor MVP | One real authorization-removal commit pair runs source → IR → graph → risk → verdict → dashboard end to end |
| 9 | AI explanation layer | Ollama schema-constrained explanations and HTTP-test proposals from structured evidence, with deterministic degraded mode |
| 10 | Validation and decision | Hardened Docker-only HTTP authorization validation and final ALLOW / REVIEW / BLOCK decision policy |
| 11 | Pull-request integration | GitHub Action/App, Check annotations, SARIF 2.1.0 output, stable finding fingerprints, and PR summary |
| 12 | Evaluation and demo freeze | Labeled corpus, precision/recall/F1, false-positive analysis, performance report, and tests on 2–4 OSS Spring Boot repos |
| 13* | Product polish | Permanent regression tests from confirmed findings, dashboard polish, and conditional Python proof of concept |
| 14 | Buffer and write-up | Reliability fixes, report/paper draft, architecture diagrams, and demo recording |
| 15 | Release polish | README, reproducible deployment, security review, and rehearsed module deep-dives |
| 16 (~Dec 1) | Submit | Final presentation, tagged release, evidence bundle, and submission |

\* The Python proof of concept starts only after the Week 12 Java MVP and evaluation
gate passes. It must preserve the IR contract and must not delay the core Java/Spring
Boot deliverable.

**Roadmap quality gates:** a week is complete only when its deliverable is automated,
tested, documented, and reproducible from a clean checkout. Every evidence-bearing
analysis result must include repository/commit identity, source locations, analyzer
version, coverage/diagnostics, and deterministic inputs. Risk and confidence are
separate values: risk estimates impact; confidence reports extraction reliability.

### Post-MVP tracks

1. **Python web analysis — FastAPI first. In progress, not yet promoted.**
   `services/python-analyzer/` ships framework detection and a separate analyzer
   that uses Python AST evidence and emits the unchanged Stage 3 IR. The only
   supported target is Python 3 FastAPI, not every Python repository. Python
   without a supported framework returns `UNSUPPORTED_FRAMEWORK` with no
   ALLOW/BLOCK verdict. Partial or ambiguous FastAPI extraction lowers
   confidence and forces REVIEW — an unrecognised `Depends(...)` is reported
   unauthenticated at LOW confidence rather than assumed authenticated.
   Explicit-import router prefixes, modern/decorator dependencies, unambiguous
   named cross-file calls, four-scenario parity, and a pinned public evaluation
   harness and current live Docker evaluation are implemented. Still outstanding
   before promotion: independently reviewed FastAPI labels, mixed-module partial
   coverage, and type-aware attribute/service dispatch. Django and Flask require
   later, separate adapters.
2. **Notification manager.** Consume final platform decision events independently of
   the source language. Notify only verified RiskGraph users whose repository access
   is rechecked at delivery time. Start with in-app and email notifications for REVIEW
   and BLOCK; browser/mobile push may follow, while SMS or third-party messaging needs
   a separate consent, cost, and privacy review. Use durable outbox delivery,
   preferences, severity thresholds, deduplication by finding fingerprint and PR head,
   bounded retries, rate limits, delivery/audit records, and unsubscribe controls.
   Messages must distinguish possible from Docker-confirmed findings, link to protected
   evidence, and never include raw source, credentials, or sensitive response bodies.
   The transactional outbox, in-app/email delivery, live access checks, verified-email
   gate, per-repository unsubscribe, retries, audit rows, deduplication, and read state
   are implemented. Optional resolution notices, administrator recipient
   presets, and full PostgreSQL/provider recovery tests remain before promotion.

Detailed sequencing and acceptance criteria are maintained in
`docs/pending-updates.md`.

---

## 9. The four MVP demo scenarios (must all work reliably for the final demo)

1. **Authorization removal** — `@PreAuthorize("hasRole('ADMIN')")` deleted from
   `/admin/export` → risk 22→91, ALLOW becomes BLOCK.
2. **Safe change** — cosmetic change (e.g. product description formatting) →
   risk unchanged, 0 new paths, ALLOW. (Proves the system doesn't cry wolf.)
3. **New public sensitive endpoint** — a newly added unauthenticated endpoint reaches
   a HIGH-sensitivity resource → new path, increased risk, and BLOCK/REVIEW by policy.
4. **Sensitive resource exposure** — an existing public route begins reaching a
   sensitive repository/resource → new path and increased risk without requiring
   IDOR or taint-flow analysis.

Existing IDOR and ADMIN→USER privilege-expansion fixtures are retained only as
`PHASE 2` design scaffolds. They are not MVP acceptance criteria and must not be
presented as implemented until the corresponding deterministic analysis exists.

---

## 10. Module ownership (so Codex knows whose "voice"/conventions apply where)

| Module | Owner focus | Primary language |
|---|---|---|
| Code Analysis | Git diff, AST parsing, endpoint/auth extraction, IR output | Java |
| Security Graph & Risk | Graph construction, BFS/DFS, reachability, risk scoring | Python |
| AI & Validation | LLM integration, hypothesis generation, test generation, validation | Python |
| Platform & DevOps | Spring Boot platform, Postgres, auth, React dashboard, GitHub integration, Docker/CI | Java + TypeScript |

When generating code for a module, follow that module's primary language and keep
changes scoped to that module's folder — don't reach across module boundaries
without flagging it.

---

## 11. What the AI layer (inside the product) must never do

This is a rule about the product's own LLM calls, not about Codex itself:
- Never let the LLM decide graph edges, access rules, or risk scores directly
- Never let the LLM's raw text output be treated as a "confirmed" finding —
  only a validated test result can confirm a finding
- Always pass structured JSON evidence in, and validate structured JSON schema out
  (Section 4) — reject free-text-only responses from the model

---

## 12. Working agreement for Codex

- When a request is ambiguous, prefer the smallest change consistent with the IR
  contract and current week's scope rather than guessing at a bigger feature.
- Never generate code that sends security tests to any non-Docker/non-local target.
- Flag (don't silently ignore) any request that conflicts with Section 6's boundaries.
- Keep commits scoped and incremental — this repo's commit history is part of the
  academic/portfolio deliverable, so avoid large unexplained dumps.

---

## 13. Local LLM backend — Ollama

Stages 7 and 8 (AI reasoning/hypothesis and AI test generation — see Section 2) default
to a **local Ollama instance** rather than a hosted API, for cost, offline reliability
during demos, and data-privacy reasons. Codex must implement the LLM client as a
swappable interface so a hosted provider can be added later without touching the
graph/risk/decision code (those stages never call an LLM at all — see Section 11).

**Connection config — never hardcode host/IP/model in code:**
- `OLLAMA_BASE_URL` env var, defaulting to `http://localhost:11434` if unset.
- If Ollama runs on a different machine/VM than the AI service (e.g. Ollama on a
  Kali VM, backend running elsewhere), set `OLLAMA_BASE_URL=http://<kali-vm-ip>:11434`.
  This requires Ollama to be started with `OLLAMA_HOST=0.0.0.0:11434 ollama serve` so
  it listens on all interfaces, not just localhost — confirm reachability with
  `curl http://<kali-vm-ip>:11434/api/tags` before writing any client code against it.
- `OLLAMA_MODEL` env var for the model name (e.g. `llama3.1:8b`, `qwen2.5:7b`) —
  prefer models with strong JSON-schema instruction-following for this task.

**Structured output — mandatory, not optional:**
- Every call to Ollama for Stage 7/8 MUST use Ollama's schema-constrained `format`
  parameter (pass the JSON Schema, not a "please return JSON" prompt instruction) so
  the response is guaranteed to match the schemas in Section 4.
- Always validate the parsed response against the corresponding Pydantic model before
  treating it as a finding — schema-constrained output can still contain low-quality
  or wrong *content* even when the shape is guaranteed correct. Validation belongs to
  the deterministic layer, never trust LLM output on its own (see Section 11).

**Minimal client shape (Python, FastAPI service):**
```python
import os, httpx

OLLAMA_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3.1:8b")


async def ask_ollama(prompt: str, schema: dict) -> dict:
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": OLLAMA_MODEL,
                "prompt": prompt,
                "format": schema,  # JSON Schema — constrained decoding, not free text
                "stream": False,
            },
        )
        resp.raise_for_status()
        return resp.json()
```

**Fallback path (optional, do not build until MVP is stable):** if a
`HOSTED_LLM_API_KEY` env var is set, the client may route to a hosted provider
instead of Ollama. This should be a config switch behind the same interface, never
a separate code path or a fork of the calling logic.
