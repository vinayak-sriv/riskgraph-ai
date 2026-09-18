import asyncio
import os

import httpx
from app.main import app

TEST_SERVICE_TOKEN = os.environ.setdefault("RISKGRAPH_ANALYZER_SERVICE_TOKEN", "test-analyzer-token")


def post(
    path: str,
    payload: dict,
    *,
    authenticated: bool = True,
    token: str = TEST_SERVICE_TOKEN,
) -> httpx.Response:
    async def send() -> httpx.Response:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            headers = {"X-RiskGraph-Service-Token": token} if authenticated else {}
            return await client.post(path, json=payload, headers=headers)

    return asyncio.run(send())


def test_analyze_returns_a_stub_envelope_with_zero_endpoints(tmp_path):
    payload = {"repository_path": str(tmp_path), "old_commit": "a" * 40, "new_commit": "b" * 40}
    response = post("/analyze", payload)

    assert response.status_code == 200
    body = response.json()
    assert body["before"] == []
    assert body["after"] == []
    assert body["provenance"]["repository_path"] == str(tmp_path)


def test_analyze_rejects_missing_service_credentials(tmp_path):
    payload = {"repository_path": str(tmp_path), "old_commit": "a" * 40, "new_commit": "b" * 40}
    assert post("/analyze", payload, authenticated=False).status_code == 401


def test_analyze_rejects_another_services_credential(tmp_path):
    payload = {"repository_path": str(tmp_path), "old_commit": "a" * 40, "new_commit": "b" * 40}
    assert post("/analyze", payload, token="test-graph-token").status_code == 401


def test_analyze_rejects_malformed_commit_shas(tmp_path):
    payload = {"repository_path": str(tmp_path), "old_commit": "not-a-sha", "new_commit": "b" * 40}
    assert post("/analyze", payload).status_code == 422


def test_health_needs_no_credential():
    async def send() -> httpx.Response:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            return await client.get("/health")

    response = asyncio.run(send())
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "python-analyzer"}
