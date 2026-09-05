# ADR-0003: Ollama as Local LLM Backend

## Status

Accepted.

## Decision

The AI layer defaults to Ollama through `OLLAMA_BASE_URL` and `OLLAMA_MODEL`.
Ollama is external to the service and may run on a Kali VM.

## Consequences

- The project can demo without hosted LLM costs.
- LLM configuration is environment-based, not hardcoded.
- A hosted provider can be added later behind the same client interface.
