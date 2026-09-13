import asyncio
import json

import httpx
from ai_app.reasoning import EvidenceRequest, OllamaProvider, explain, redact

REQUEST = EvidenceRequest(
    evidence=["ADMIN authorization removed"], method="GET", path="/admin/export"
)
VALID = dict(
    finding="Authorization removal",
    evidence=REQUEST.evidence,
    hypothesis="Unauthenticated access may be possible",
    recommended_test="GET /admin/export without authentication",
    confidence="HIGH",
)


def test_ollama_receives_schema_and_only_structured_evidence():
    calls = []

    def handler(request):
        calls.append(request.url.path)
        if request.url.path == "/api/tags":
            return httpx.Response(200, json={"models": []})
        body = json.loads(request.content)
        assert body["format"]["additionalProperties"] is False
        assert body["format"]["required"] == list(VALID)
        assert body["format"]["properties"]["hypothesis"]["maxLength"] == 512
        assert body["format"]["properties"]["evidence"]["items"]["enum"] == REQUEST.evidence
        assert body["format"]["properties"]["evidence"]["maxItems"] == 1
        assert (
            body["format"]["properties"]["recommended_test"]["const"] == VALID["recommended_test"]
        )
        assert body["stream"] is False
        assert "risk_after" not in body["prompt"]
        return httpx.Response(200, json={"response": json.dumps(VALID)})

    result = asyncio.run(explain(REQUEST, OllamaProvider(httpx.MockTransport(handler))))
    assert calls == ["/api/tags", "/api/generate"]
    assert result.status == "AVAILABLE" and result.confirmed is False


def test_ollama_reuses_connections_and_cached_model_probe():
    calls = []

    def handler(request):
        calls.append(request.url.path)
        if request.url.path == "/api/tags":
            return httpx.Response(200, json={"models": []})
        return httpx.Response(200, json={"response": json.dumps(VALID)})

    async def run():
        provider = OllamaProvider(httpx.MockTransport(handler))
        await explain(REQUEST, provider)
        await explain(REQUEST, provider)
        assert provider.client is not None
        await provider.close()
        assert provider.client is None

    asyncio.run(run())
    assert calls == ["/api/tags", "/api/generate", "/api/generate"]


def test_invalid_invented_and_authoritative_output_is_rejected():
    for output in [
        "free text",
        {**VALID, "risk_after": 0},
        {**VALID, "evidence": ["invented"]},
        {**VALID, "recommended_test": "POST /admin/export without authentication"},
    ]:

        class Provider:
            async def generate(self, evidence, schema, result=output):
                return result

        result = asyncio.run(explain(REQUEST, Provider()))
        assert result.status == "DEGRADED"
        assert result.reason_code == "INVALID_AI_OUTPUT"


def test_unavailable_is_deterministic_degraded():
    def handler(request):
        raise httpx.ConnectError("offline")

    provider = OllamaProvider(httpx.MockTransport(handler))
    one = asyncio.run(explain(REQUEST, provider))
    two = asyncio.run(explain(REQUEST, provider))
    assert one == two and one.status == "DEGRADED"
    assert one.analysis.recommended_test == "GET /admin/export without authentication"


def test_secret_redaction():
    result = redact("token=abc password=pw C:\\Users\\person\\repo Bearer xyz")
    assert all(secret not in result for secret in ["abc", "pw", "person", "xyz"])


def test_generation_budget_rejects_oversized_evidence_without_sending_it():
    def handler(request):
        raise AssertionError("Oversized payload must never be sent")

    result = asyncio.run(
        explain(
            EvidenceRequest(evidence=["x" * 2000] * 10),
            OllamaProvider(httpx.MockTransport(handler)),
        )
    )
    assert result.status == "DEGRADED" and result.reason_code == "INVALID_AI_OUTPUT"
