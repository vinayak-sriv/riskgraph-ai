import hashlib
import importlib.util
import json
import re
from pathlib import Path

import pytest
from app.main import analyze
from app.models import AnalysisRequest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "regression_generator", ROOT / "tools/regression/generate.py"
)
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


def test_confirmed_findings_remain_permanent_graph_and_risk_regressions() -> None:
    paths = sorted((ROOT / "datasets/regressions").glob("*.json"))
    assert paths, "At least one confirmed regression is required"
    for path in paths:
        case = json.loads(path.read_text(encoding="utf-8"))
        content = {key: value for key, value in case.items() if key != "content_sha256"}
        canonical = json.dumps(content, sort_keys=True, separators=(",", ":")).encode()
        assert case["content_sha256"] == hashlib.sha256(canonical).hexdigest()

        confirmation = case["confirmation"]
        assert confirmation["status"] == "CONFIRMED"
        assert re.fullmatch(r"sha256:[0-9a-f]{64}", confirmation["container_image_id"])
        if confirmation["probe_image_id"] is not None:
            assert re.fullmatch(r"sha256:[0-9a-f]{64}", confirmation["probe_image_id"])
        assert re.fullmatch(r"[0-9a-f]{64}", confirmation["response_sha256"])
        assert confirmation["source_commit"] == case["source"]["new_commit"]
        assert confirmation["cleanup_complete"] is True

        result = analyze(AnalysisRequest(before=case["before"], after=case["after"]))
        expected = case["expected"]
        assert result.risk_result.risk_before == expected["risk_before"]
        assert result.risk_result.risk_after == expected["risk_after"]
        assert result.risk_result.risk_delta == expected["risk_delta"]
        assert result.verdict == expected["verdict"]
        assert len(result.graph_delta.new_paths) == expected["new_paths"]


def test_generator_rejects_unconfirmed_evidence() -> None:
    with pytest.raises(ValueError, match="Only a CONFIRMED"):
        GENERATOR.build_case({"validation": {"status": "INCONCLUSIVE"}})


def test_generator_rejects_mutable_probe_provenance() -> None:
    with pytest.raises(ValueError, match="probe image ID must be immutable"):
        GENERATOR.build_case(
            {
                "validation": {
                    "status": "CONFIRMED",
                    "confirmed": True,
                    "cleanup_complete": True,
                    "container_image_id": "sha256:" + "a" * 64,
                    "probe_image_id": "python:latest",
                    "response_sha256": "b" * 64,
                    "source_commit": "c" * 40,
                },
                "provenance": {"new_commit": "c" * 40},
            }
        )
