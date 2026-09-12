import asyncio
import os

import httpx
from ai_app.main import app
from ai_app.reasoning import AIAnalysis, Explanation
from ai_app.validation import ValidationResult

TEST_SERVICE_TOKEN = os.environ.setdefault("RISKGRAPH_SERVICE_TOKEN", "test-internal-token")


def call(method, route, payload=None, *, authenticated=True):
    async def send():
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            headers = {"X-RiskGraph-Service-Token": TEST_SERVICE_TOKEN} if authenticated else {}
            return await client.request(method, route, json=payload, headers=headers)

    return asyncio.run(send())


def test_health_does_not_require_ollama_or_docker():
    assert call("GET", "/health").status_code == 200


def test_internal_routes_reject_missing_service_credentials():
    assert (
        call("POST", "/ai/analyze", {"evidence": ["fact"]}, authenticated=False).status_code == 401
    )


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
