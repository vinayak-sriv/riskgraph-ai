import asyncio
import os

import httpx
from ai_app.main import app
from ai_app.reasoning import AIAnalysis, Explanation
from ai_app.validation import ValidationResult

TEST_SERVICE_TOKEN = os.environ.setdefault("RISKGRAPH_AI_SERVICE_TOKEN", "test-ai-token")


def call(method, route, payload=None, *, authenticated=True, token=TEST_SERVICE_TOKEN):
    async def send():
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            headers = {"X-RiskGraph-Service-Token": token} if authenticated else {}
            return await client.request(method, route, json=payload, headers=headers)

    return asyncio.run(send())


def test_health_is_minimal_and_does_not_expose_provider_topology(monkeypatch):
    monkeypatch.setenv("OLLAMA_BASE_URL", "http://internal-ollama.example:11434")
    monkeypatch.setenv("OLLAMA_MODEL", "private-model-name")

    response = call("GET", "/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ai-validation-service"}
    assert "internal-ollama" not in response.text
    assert "private-model-name" not in response.text


def test_internal_routes_reject_missing_service_credentials():
    assert (
        call("POST", "/ai/analyze", {"evidence": ["fact"]}, authenticated=False).status_code == 401
    )


def test_internal_routes_reject_another_services_credential():
    response = call(
        "POST",
        "/ai/analyze",
        {"evidence": ["fact"]},
        token="test-graph-token",
    )
    assert response.status_code == 401


def test_openapi_requires_internal_service_authentication():
    schema = app.openapi()
    health_schema = schema["components"]["schemas"]["HealthResponse"]
    assert health_schema["additionalProperties"] is False
    assert health_schema["required"] == ["status", "service"]
    assert schema["paths"]["/health"]["get"]["responses"]["200"]["content"]["application/json"][
        "schema"
    ] == {"$ref": "#/components/schemas/HealthResponse"}
    scheme = schema["components"]["securitySchemes"]["APIKeyHeader"]
    assert scheme == {
        "type": "apiKey",
        "in": "header",
        "name": "X-RiskGraph-Service-Token",
    }
    for route in ("/ai/analyze", "/ai/test-suggestion", "/validation/http"):
        assert schema["paths"][route]["post"]["security"] == [{"APIKeyHeader": []}]


def test_invalid_public_target_fails_before_runner():
    response = call(
        "POST",
        "/validation/http",
        {"target_base_url": "https://example.com", "method": "GET", "path": "/"},
    )
    assert response.status_code == 422


def test_routes_return_validated_outputs(monkeypatch):
    async def explain(request, provider=None):
        return Explanation(
            status="DEGRADED",
            provider="test",
            reason_code="OFFLINE",
            analysis=AIAnalysis(
                finding="Test",
                evidence=request.evidence,
                hypothesis="Unconfirmed",
                recommended_test="GET /admin/export",
                confidence="LOW",
            ),
        )

    async def validate(request):
        return ValidationResult(
            status="ERROR", reason_code="OFFLINE", sandbox_revision=request.sandbox_revision
        )

    monkeypatch.setattr("ai_app.main.explain", explain)
    monkeypatch.setattr("ai_app.main.validate", validate)
    for route in ("/ai/analyze", "/ai/test-suggestion"):
        response = call("POST", route, {"evidence": ["Test fact"]})
        assert response.status_code == 200 and response.json()["confirmed"] is False
    assert (
        call("POST", "/validation/http", {"sandbox_revision": "protected"}).json()["status"]
        == "ERROR"
    )
