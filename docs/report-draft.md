# RiskGraph AI technical report draft

## Abstract

RiskGraph AI evaluates security-relevant changes in Java and Spring Boot pull
requests. It extracts annotation-based authorization and application structure from
two immutable revisions, normalizes that evidence into a versioned intermediate
representation, builds before-and-after security graphs, and detects newly reachable
paths from untrusted identities to sensitive resources. A transparent weighted model
reports risk before, risk after, and the delta. A local language model may explain
the deterministic evidence and propose a constrained HTTP authorization test, but it
cannot create graph facts, assign risk, or confirm a vulnerability. Confirmation is
reserved for a fixed probe executed against a registered Docker sandbox.

The implemented academic MVP covers authorization removal, new public sensitive
endpoints, and sensitive resource exposure in annotation-secured Spring applications.
It produces dashboard evidence, GitHub Check data, SARIF 2.1.0, stable finding
fingerprints, and a deterministic source package. Synthetic results demonstrate
regression behavior rather than real-world vulnerability accuracy. Two pinned public
Spring repositories provide provisional extraction evidence; their independent human
labels remain an explicit release gate.

## Problem and research question

Line-oriented review can show that an annotation or method call changed without
showing the resulting security path. The project asks whether a pull-request tool can
turn a small Java change into reproducible structural evidence: what became reachable,
which source locations support that conclusion, how much deterministic risk changed,
and whether a local sandbox reproduces the expected authorization behavior.

The design deliberately separates four kinds of claims. Static extraction reports
source evidence. Graph algorithms report reachability. The risk engine estimates
impact through published weights. AI provides advisory interpretation. Only sandbox
execution may confirm the proposed HTTP behavior.

## Method

The analyzer acquires only allowlisted local repositories and resolves two full commit
identities with JGit. Spoon parses the affected conventional Java source roots and
extracts Spring routes, HTTP methods, controllers, supported authorization annotations,
service calls, repositories, resources, and source locations. Unsupported or ambiguous
constructs produce diagnostics and reduce confidence instead of silently widening a
claim.

Downstream services consume the locked endpoint IR rather than raw source. NetworkX
constructs stable before-and-after graphs and applies reachability to the same
route/resource identity. The risk engine uses the documented six-component weighted
formula. Risk and confidence remain separate: risk describes potential impact while
confidence describes extraction reliability.

Ollama receives structured evidence and a JSON Schema. Pydantic validates the result.
If the model is unavailable or invalid, the product exposes a deterministic degraded
state and retains the graph/risk result. Validation uses an isolated Docker daemon,
digest-bound images, an internal network, resource limits, a read-only filesystem,
and a fixed HTTP probe. The final policy combines deterministic findings with
validation status into ALLOW, REVIEW, or BLOCK.

## Implementation

The platform API is a Spring Boot service that owns orchestration, PostgreSQL writes,
sessions, access control, scan history, and final decisions. The Java analyzer owns
source acquisition and Spoon extraction. Python FastAPI services own NetworkX graph
analysis, risk scoring, AI interpretation, and sandbox validation. A React and
TypeScript dashboard presents risk deltas, before/after graphs, source evidence,
confidence, diagnostics, validation provenance, and saved scans.

Contracts are versioned JSON Schema and OpenAPI files. PostgreSQL migrations are
managed through Flyway. GitHub Actions run Python, Maven, React, browser, source-secret,
container, and release-package checks. Runtime images use pinned bases and non-root
application users where the base entrypoint permits it.

## Evaluation

The four authored source scenarios exercise authorization removal, a safe cosmetic
change, a new public sensitive endpoint, and sensitive resource exposure. The primary
authorization-removal fixture changes risk from 22 to 91, creates one anonymous path,
and returns BLOCK after the sandbox confirms the unauthorized response. The safe
change remains 22 to 22 with no new path and ALLOW. The two public-sensitive scenarios
produce a new path and BLOCK.

The 100-record synthetic corpus is divided into development, calibration, and test
groups. Its graph and verdict checks currently report perfect synthetic precision,
recall, and F1. Those values measure canonical IR-to-graph regression behavior only;
they are not claims about real repositories. The evaluator records this provenance
and leaves source-extraction and real-world vulnerability metrics unset.

Two pinned Apache-2.0 Spring repositories exercise external extraction. On the
post-0.4.1 run, both cases matched two of two expected endpoint/authentication rows
and returned REVIEW with LOW confidence because deterministic resource coverage was
incomplete. Mean analyzer runtimes were approximately 9.5 seconds for Spring
Petclinic and 5.6 seconds for the REST guide on the recorded host. The labels remain
provisional pending an independent source reviewer.

A small synthetic cache benchmark reduced an identical repeated Java analysis from
4,252 ms to 95 ms while preserving changed files, IR, diagnostics, coverage, and
provenance. This demonstrates the cache path and is not a production latency claim.
The Docker release gate validated all four scenarios and found no fixed critical
vulnerabilities across the 12 scanned runtime and sandbox images at the time of the
recorded run.

## Threats to validity and limitations

The MVP supports Spring annotation-based authorization only. It does not interpret
`SecurityFilterChain`, perform taint tracking, detect IDOR, support multiple languages,
or authorize testing against public systems. Sensitivity comes from a versioned policy
rather than whole-program data-flow analysis. Call resolution deliberately fails
closed when Spoon cannot establish a deterministic path.

The synthetic corpus reuses authored scenario families and therefore cannot estimate
field accuracy. The two external cases are negative changes and do not establish
vulnerability precision or recall. Local Docker results demonstrate authored sandbox
behavior, not exploitability of arbitrary applications. Ollama output can still be
wrong despite schema-valid structure, which is why it remains advisory.

## Conclusion

RiskGraph AI demonstrates a contract-first way to combine program analysis, graph
reachability, transparent risk scoring, advisory AI, and isolated validation without
allowing an LLM to become the security authority. The strongest current result is a
reproducible Java/Spring vertical slice with explicit provenance and fail-closed
behavior. The next release decision depends on independent external labels rather
than additional feature breadth.

## Reproducibility references

- [Architecture](architecture.md)
- [IR contract](ir-contract.md)
- [Risk scoring](risk-scoring.md)
- [Decision policy](decision-policy.md)
- [Threat model](threat-model.md)
- [Runtime guide](runtime-guide.md)
- [Release rehearsal](release-rehearsal.md)
- [External evaluation](../README.md#pinned-external-spring-checks)
