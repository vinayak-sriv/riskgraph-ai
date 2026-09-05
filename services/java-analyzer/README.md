# Java Analyzer

Java service for Git diff analysis and Spring Boot annotation extraction.

Architecture constraints:
- Outputs endpoint IR matching `contracts/ir/endpoint-ir.schema.json`.
- Handles annotation-based authorization only for MVP.
- Does not assign risk, call AI, or write to PostgreSQL.

Current state: the service boundary compiles and exposes health/analyze scaffolds.
Week 4 adds secure Git acquisition and deterministic diffing; Weeks 5–6 add Spoon
extraction and schema-valid before/after IR.
