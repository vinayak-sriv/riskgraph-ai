import asyncio
import json
import os
from pathlib import Path

import httpx
from app.main import app
from jsonschema import Draft202012Validator

TEST_SERVICE_TOKEN = os.environ.setdefault("RISKGRAPH_SERVICE_TOKEN", "test-internal-token")


def post(path: str, payload: dict, *, authenticated: bool = True) -> httpx.Response:
    async def send() -> httpx.Response:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            headers = {"X-RiskGraph-Service-Token": TEST_SERVICE_TOKEN} if authenticated else {}
            return await client.post(path, json=payload, headers=headers)

    return asyncio.run(send())


def test_analysis_endpoint_returns_contract_shaped_demo(
    authorization_removal_payload: dict,
) -> None:
    response = post("/analysis", authorization_removal_payload)

    assert response.status_code == 200
    body = response.json()
    assert body["verdict"] == "BLOCK"
    assert body["risk_result"]["risk_before"] == 22
    assert body["risk_result"]["risk_after"] == 91
    assert len(body["graph_delta"]["new_paths"]) == 1


def test_internal_routes_reject_missing_service_credentials(
    authorization_removal_payload: dict,
) -> None:
    assert post("/analysis", authorization_removal_payload, authenticated=False).status_code == 401


def test_graph_and_risk_responses_match_locked_json_schemas(
    authorization_removal_payload: dict,
) -> None:
    graph_response = post("/graph/delta", authorization_removal_payload)
    assert graph_response.status_code == 200
    graph = graph_response.json()
    risk_response = post(
        "/risk/score",
        {**authorization_removal_payload, "graph_delta": graph},
    )
    assert risk_response.status_code == 200

    root = Path(__file__).resolve().parents[2]
    graph_schema = json.loads(
        (root / "contracts" / "ir" / "graph-delta.schema.json").read_text(encoding="utf-8")
    )
    risk_schema = json.loads(
        (root / "contracts" / "ir" / "risk-result.schema.json").read_text(encoding="utf-8")
    )
    assert list(Draft202012Validator(graph_schema).iter_errors(graph)) == []
    assert list(Draft202012Validator(risk_schema).iter_errors(risk_response.json())) == []


def test_invalid_or_extra_ir_fields_are_rejected(authorization_removal_payload: dict) -> None:
    invalid = {
        **authorization_removal_payload,
        "after": [{**authorization_removal_payload["after"][0], "unexpected": "value"}],
    }
    response = post("/analysis", invalid)

    assert response.status_code == 422


def test_risk_endpoint_requires_source_ir(authorization_removal_payload: dict) -> None:
    graph = post("/graph/delta", authorization_removal_payload).json()
    response = post("/risk/score", {"graph_delta": graph})

    assert response.status_code == 422


def test_risk_endpoint_enforces_the_contract_size_limit(
    authorization_removal_payload: dict,
) -> None:
    graph = post("/graph/delta", authorization_removal_payload).json()
    oversized = [authorization_removal_payload["before"][0]] * 2_001

    response = post(
        "/risk/score",
        {"before": oversized, "after": [], "graph_delta": graph},
    )

    assert response.status_code == 422
