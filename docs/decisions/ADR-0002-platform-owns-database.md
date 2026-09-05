# ADR-0002: Platform API Owns Database Writes

## Status

Accepted.

## Decision

`services/platform-api` is the only service allowed to write to PostgreSQL.
Analyzer, graph/risk, and AI/validation services return structured JSON results.

## Consequences

- Persistence rules stay centralized.
- Analysis services remain easier to test.
- Database schema changes do not ripple through every service.
