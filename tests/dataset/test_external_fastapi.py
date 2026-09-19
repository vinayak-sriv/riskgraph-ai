import copy
import importlib.util
import json
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "external_fastapi", ROOT / "tools/evaluation/external_fastapi.py"
)
external = importlib.util.module_from_spec(spec)
spec.loader.exec_module(external)


def test_assess_reports_full_recall_and_quality_for_a_reviewed_cosmetic_change():
    case = external.read_manifest()["cases"][0]
    rows = [
        dict(
            endpoint=dict(
                method=item["method"],
                endpoint=item["endpoint"],
                authentication=item["authentication"],
                required_role=item["required_role"],
            )
        )
        for item in case["expected_changed_endpoints"]
    ]
    scan = dict(
        source_evidence=dict(before=rows, after=rows),
        graph_delta=dict(new_paths=[]),
        risk_result=dict(risk_delta=0),
        final_verdict="REVIEW",
        quality=dict(
            confidence=case["expected_confidence"],
            coverage_ratio=case["expected_coverage_ratio"],
        ),
        coverage=dict(coverage_ratio=case["expected_coverage_ratio"]),
        diagnostics=[dict(code=case["expected_diagnostic"])],
    )
    result = external.assess(case, scan)
    assert result["expected_endpoint_auth_rows"] == len(rows) * 2
    assert result["matched_endpoint_auth_rows"] == len(rows) * 2
    assert result["endpoint_auth_recall"] == 1.0
    assert result["verdict"] == "REVIEW"
    assert result["risk_delta"] == 0
    assert result["diagnostic_codes"] == [case["expected_diagnostic"]]

    scan["final_verdict"] = "BLOCK"
    with pytest.raises(AssertionError, match="never fail closed to BLOCK"):
        external.assess(case, scan)


@pytest.mark.parametrize(
    "field,value",
    [
        ("repository", "https://example.org/target.git"),
        ("id", "../outside"),
        ("new_commit", "main"),
    ],
)
def test_external_manifest_rejects_unpinned_or_unregistered_sources(
    tmp_path, monkeypatch, field, value
):
    manifest = copy.deepcopy(external.read_manifest())
    manifest["cases"][0][field] = value
    (tmp_path / "manifest.json").write_text(json.dumps(manifest))
    monkeypatch.setattr(external, "DATA", tmp_path)
    with pytest.raises(ValueError):
        external.read_manifest()


def test_manifest_cannot_claim_human_review_without_case_attribution(tmp_path, monkeypatch):
    # ponytail: strip any case-level review the live manifest may already carry,
    # so this negative path is exercised regardless of the manifest's real state.
    manifest = copy.deepcopy(external.read_manifest())
    for case in manifest["cases"]:
        case.pop("review", None)
    manifest.update(label_status="REVIEWED", review_type="HUMAN_SOURCE_REVIEW", human_reviewed=True)
    (tmp_path / "manifest.json").write_text(json.dumps(manifest))
    monkeypatch.setattr(external, "DATA", tmp_path)

    with pytest.raises(ValueError, match="requires reviewer"):
        external.read_manifest()


def test_manifest_cannot_claim_reviewed_labels_when_not_human_reviewed(tmp_path, monkeypatch):
    manifest = copy.deepcopy(external.read_manifest())
    manifest.update(
        label_status="REVIEWED", review_type="HUMAN_SOURCE_REVIEW", human_reviewed=False
    )
    (tmp_path / "manifest.json").write_text(json.dumps(manifest))
    monkeypatch.setattr(external, "DATA", tmp_path)

    with pytest.raises(ValueError, match="must remain provisional"):
        external.read_manifest()
