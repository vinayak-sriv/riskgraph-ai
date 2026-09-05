# ADR-0001: Monorepo Contract-First Architecture

## Status

Accepted.

## Decision

RiskGraph AI uses a modular monorepo with shared contracts in `contracts/`.
Services communicate through JSON Schema/OpenAPI contracts rather than direct
internal coupling.

## Consequences

- Teams can build modules in parallel from sample fixtures.
- Contract changes become explicit and reviewable.
- The project avoids both a single tangled backend and unnecessary early
  microservice complexity.
