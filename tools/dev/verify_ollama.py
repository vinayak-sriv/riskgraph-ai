"""Live schema-constrained Ollama check. No security probe is executed here."""

import json
import os
import time
import urllib.request
from pathlib import Path

from jsonschema import Draft202012Validator
from platform_session import PlatformSession

ROOT = Path(__file__).resolve().parents[2]


def call(url, payload=None):
    headers = {"Content-Type": "application/json"}
    if payload is not None:
        token = os.environ.get("RISKGRAPH_SERVICE_TOKEN")
        if not token:
            raise RuntimeError("RISKGRAPH_SERVICE_TOKEN is required for internal API verification")
        headers["X-RiskGraph-Service-Token"] = token
    request = urllib.request.Request(
        url, data=None if payload is None else json.dumps(payload).encode(), headers=headers
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def main():
    health = call("http://localhost:8083/health")
    # Host address of the optional local Compose backend, never a test target.
    tags = call("http://localhost:11434/api/tags")
    model = next(m for m in tags["models"] if m["name"] == health["ollama_model"])
    evidence = {
        "evidence": [
            "ADMIN authorization removed",
            "Anonymous access to the Payment resource is newly reachable",
        ],
        "method": "GET",
        "path": "/admin/export",
    }
    schema = json.loads((ROOT / "contracts/ai/explanation.schema.json").read_text())
    results = {}
    for route in ("/ai/analyze", "/ai/test-suggestion"):
        started = time.perf_counter()
        result = call("http://localhost:8083" + route, evidence)
        Draft202012Validator(schema).validate(result)
        assert result["status"] == "AVAILABLE", result["reason_code"]
        assert result["confirmed"] is False
        assert set(result["analysis"]["evidence"]).issubset(evidence["evidence"])
        assert result["analysis"]["recommended_test"] == "GET /admin/export without authentication"
        results[route] = dict(response=result, seconds=round(time.perf_counter() - started, 3))
    manifest = json.loads((ROOT / "samples/generated/mvp-v1/manifest.compose.json").read_text())
    scan = PlatformSession().call("/analyses", manifest["scenarios"]["authorization-removal"])
    assert scan["ai"]["status"] == "AVAILABLE", scan["ai"]
    assert [scan["risk_result"][key] for key in ("risk_before", "risk_after", "risk_delta")] == [
        22,
        91,
        69,
    ]
    assert scan["pre_validation_verdict"] == scan["final_verdict"] == "BLOCK"
    assert len(scan["graph_delta"]["new_paths"]) == 1
    results["source_pipeline"] = dict(
        scan_id=scan["scan_id"],
        ai=scan["ai"],
        risk_before=22,
        risk_after=91,
        risk_delta=69,
        verdict="BLOCK",
    )
    output = ROOT / "tmp/evidence-bundle/live-ollama.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            dict(
                model=model["name"],
                model_digest=model["digest"],
                scope="Live integration; explanation quality is not benchmarked",
                results=results,
            ),
            indent=2,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(
        "PASS: both AI routes and source pipeline; schema valid, evidence bounded, risk 22 -> 91, BLOCK"
    )


if __name__ == "__main__":
    main()
