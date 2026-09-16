import pytest
from app.main import analyze
from app.models import AnalysisRequest


def protected_endpoint(role: str | None) -> dict:
    return {
        "endpoint": "/exports",
        "method": "GET",
        "controller": "ExportController",
        "authentication": True,
        "required_role": role,
        "service": "ExportService",
        "repository": "CustomerExportRepository",
        "resource": "CustomerExport",
        "sensitivity": "HIGH",
    }


@pytest.mark.parametrize(
    ("before_role", "after_role"),
    [
        ("ADMIN", "USER"),
        ("ADMIN", None),
        ("FINANCE", "EMPLOYEE"),
    ],
)
def test_protected_role_change_is_contained_as_unresolved_review(
    before_role: str, after_role: str | None
) -> None:
    result = analyze(
        AnalysisRequest.model_validate(
            {
                "before": [protected_endpoint(before_role)],
                "after": [protected_endpoint(after_role)],
            }
        )
    )

    assert result.verdict == "REVIEW"
    assert result.quality.confidence == "LOW"
    assert result.quality.incomplete is True
    assert "UNRESOLVED_AUTHORIZATION_DELTA" in result.reason_codes
    assert "INSUFFICIENT_EXTRACTION_EVIDENCE" in result.reason_codes
    assert any("role ordering is outside" in item for item in result.risk_result.evidence)


def test_unchanged_role_remains_supported_and_does_not_degrade() -> None:
    endpoint = protected_endpoint("ADMIN")
    result = analyze(AnalysisRequest.model_validate({"before": [endpoint], "after": [endpoint]}))

    assert result.verdict == "ALLOW"
    assert result.quality.confidence == "HIGH"
    assert result.quality.incomplete is False
    assert "UNRESOLVED_AUTHORIZATION_DELTA" not in result.reason_codes


def test_public_to_protected_transition_remains_a_supported_strengthening() -> None:
    public = {**protected_endpoint(None), "authentication": False}
    protected = protected_endpoint("ADMIN")
    result = analyze(AnalysisRequest.model_validate({"before": [public], "after": [protected]}))

    assert result.verdict == "ALLOW"
    assert result.quality.confidence == "HIGH"
    assert "UNRESOLVED_AUTHORIZATION_DELTA" not in result.reason_codes
