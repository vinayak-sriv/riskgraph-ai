from app.main import analyze
from app.models import AnalysisRequest


def test_new_public_defaulted_repository_resource_requires_review() -> None:
    unknown_repository_resource = {
        "endpoint": "/ledger",
        "method": "GET",
        "controller": "LedgerController",
        "authentication": False,
        "required_role": None,
        "service": "LedgerService",
        "repository": "AuditLedgerRepository",
        "resource": "AuditLedger",
        "sensitivity": "MEDIUM",
    }

    result = analyze(
        AnalysisRequest.model_validate({"before": [], "after": [unknown_repository_resource]})
    )

    assert len(result.graph_delta.new_paths) == 1
    assert result.risk_result.risk_after == 57
    assert result.verdict == "REVIEW"
    assert "NEW_ANONYMOUS_SENSITIVE_PATH" in result.reason_codes


def test_unmatched_non_repository_route_can_remain_non_sensitive() -> None:
    public_status = {
        "endpoint": "/status",
        "method": "GET",
        "controller": "StatusController",
        "authentication": False,
        "required_role": None,
        "service": None,
        "repository": None,
        "resource": "Status",
        "sensitivity": "MODERATE",
    }

    result = analyze(AnalysisRequest.model_validate({"before": [], "after": [public_status]}))

    assert result.graph_delta.new_paths == []
    assert result.verdict == "ALLOW"
