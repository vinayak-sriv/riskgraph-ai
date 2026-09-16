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


def test_secret_patterns_are_scrubbed_immediately_before_inference():
    class Provider:
        async def generate(self, evidence, schema):
            assert evidence == {
                "evidence": ["token=[REDACTED] was found"],
                "method": "GET",
                "path": "/admin/export",
            }
            return {**VALID, "evidence": evidence["evidence"]}

    request = EvidenceRequest(
        evidence=["token=should-not-leave was found"],
        method="GET",
        path="/admin/export",
    )
    assert asyncio.run(explain(request, Provider())).status == "AVAILABLE"


def test_raw_source_is_rejected_before_provider_call():
    class Provider:
        async def generate(self, evidence, schema):
            raise AssertionError("Raw source must never reach the provider")

    request = EvidenceRequest(
        evidence=["package demo;\nclass SecretConfig {"],
        method="GET",
        path="/admin/export",
    )
    result = asyncio.run(explain(request, Provider()))
    assert result.status == "DEGRADED"
    assert result.reason_code == "AI_EVIDENCE_REJECTED"


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


def test_ollama_enforces_one_global_inference_budget(monkeypatch):
    monkeypatch.setenv("RISKGRAPH_AI_MAX_CONCURRENCY", "2")
    monkeypatch.setenv("RISKGRAPH_AI_QUEUE_TIMEOUT_SECONDS", "0.05")

    class SlowProvider(OllamaProvider):
        def __init__(self):
            super().__init__()
            self.active = 0
            self.maximum_active = 0
            self.started = asyncio.Event()
            self.release = asyncio.Event()

        async def _generate(self, evidence, schema):
            self.active += 1
            self.maximum_active = max(self.maximum_active, self.active)
            if self.active == 2:
                self.started.set()
            try:
                await self.release.wait()
                return VALID
            finally:
                self.active -= 1

    async def run():
        provider = SlowProvider()
        first = asyncio.create_task(explain(REQUEST, provider))
        second = asyncio.create_task(explain(REQUEST, provider))
        await provider.started.wait()
        overloaded = await explain(REQUEST, provider)
        provider.release.set()
        completed = await asyncio.gather(first, second)
        assert overloaded.status == "DEGRADED"
        assert overloaded.reason_code == "OLLAMA_CAPACITY_EXCEEDED"
        assert all(result.status == "AVAILABLE" for result in completed)
        assert provider.maximum_active == 2

    asyncio.run(run())
