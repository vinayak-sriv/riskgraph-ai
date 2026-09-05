# ADR-0004: AI Is Not the Source of Truth

## Status

Accepted.

## Decision

AI can explain evidence and propose tests, but it cannot create graph edges,
assign risk scores, or confirm vulnerabilities. Confirmation requires deterministic
validation output.

## Consequences

- Findings remain explainable.
- LLM errors do not directly become security decisions.
- The architecture matches the core discipline in `AGENTS.md`.
