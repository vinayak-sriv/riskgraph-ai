# AI and validation service

This FastAPI service provides optional Ollama-backed explanations, HTTP-test
suggestions, and local Docker validation.

## API

- `POST /ai/analyze`: explain structured deterministic evidence.
- `POST /ai/test-suggestion`: propose a schema-constrained HTTP authorization test.
- `POST /validation/http`: run an approved probe against a registered local sandbox.
- `GET /health`: service health.

## Safety boundaries

- AI output is schema constrained and validated before use.
- The service never assigns risk, creates graph edges, or confirms a vulnerability.
- Validation accepts fixed HTTP probe inputs and registered sandbox images only.
- The default runner uses a private Docker-in-Docker daemon, not the host socket.
- Remote, public, and third-party targets are rejected.
- Missing or invalid AI output produces an explicit deterministic degraded result.

Configuration is environment based. See the root [runtime guide](../../docs/runtime-guide.md)
for supported Ollama and validation settings.

## Verification

From the repository root:

```powershell
python -m pytest tests/ai_validation -q
ruff check services/ai-validation-service tests/ai_validation
```
