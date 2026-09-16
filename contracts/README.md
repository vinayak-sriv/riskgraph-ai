# Contracts

This folder is the source of truth for communication between RiskGraph modules.
Services must depend on these schemas instead of depending on another service's
internal code or database tables.

## Folders

- `ir/`: endpoint IR, analyzer envelope, graph delta, and deterministic risk result schemas.
- `ai/`: schema-constrained Ollama outputs for explanation and test suggestion.
- `validation/`: HTTP validation input and result schemas.
- `api/`: OpenAPI definitions for implemented service boundaries.

## Contract Rules

- Do not rename fields without updating all schemas, examples, docs, and consumers.
- Additive optional fields are safer than changing required fields.
- Downstream graph/risk code consumes IR JSON, not source code.
- LLM calls must use the AI schemas through Ollama's `format` parameter.
- Validation targets must stay local or Docker sandbox scoped.

## Canonical Fixtures

Examples under `contracts/**/examples/` are intentionally small and deterministic.
They support contract, reporting, source-analysis, and end-to-end regression tests.
Phase 2 design fixtures are clearly labeled and are not current acceptance criteria.
