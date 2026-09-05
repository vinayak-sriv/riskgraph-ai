# AI Validation Service

Python/FastAPI service for Ollama-backed explanation, test suggestion, and
Docker-only validation.

Architecture constraints:
- Uses `OLLAMA_BASE_URL` and `OLLAMA_MODEL`; never hardcodes the Ollama host.
- Requires schema-constrained Ollama output.
- Validates all AI output before use.
- Runs validation only against local Docker sandbox targets.
- Does not assign risk scores or graph edges.

Current state: request boundaries and explicit unimplemented routes are scaffolded.
Ollama reasoning is scheduled for Week 9 and Docker-only HTTP validation for Week 10.
