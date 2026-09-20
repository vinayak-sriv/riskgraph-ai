"""Regression tests for verdict paths that previously failed open."""

import pytest
from app.graph_engine import compare_graphs
from app.models import AnalysisRequest
from app.risk_engine import BANDS, category_for, decide, score_risk


def _endpoint(**overrides: object) -> dict:
    base = {
        "endpoint": "/reports",
        "method": "GET",
        "controller": "ReportController",
        "authentication": True,
        "required_role": "ADMIN",
        "service": "ReportService",
        "repository": "ReportRepository",
        "resource": "Report",
        "sensitivity": "MODERATE",
    }
    return {**base, **overrides}


def _score(before: list[dict], after: list[dict]):
    request = AnalysisRequest.model_validate({"before": before, "after": after})
    delta = compare_graphs(request.before, request.after)
    return score_risk(request.before, request.after, delta), delta


@pytest.mark.parametrize("sensitivity", ["LOW", "MODERATE"])
def test_authorization_removal_scores_even_without_a_new_sensitive_path(
    sensitivity: str,
) -> None:
    """A removed admin gate must score, not just appear in the evidence text.

    Below MEDIUM sensitivity no anonymous->sensitive path is emitted, so this
    never reaches the per-route scoring branch. It previously scored 0/ALLOW.
    """
    before = [_endpoint(sensitivity=sensitivity)]
    after = [_endpoint(sensitivity=sensitivity, authentication=False, required_role=None)]
    risk, delta = _score(before, after)

    assert delta.new_paths == []
    assert risk.components.authorization_change.score == 100
    assert risk.components.privilege_impact.score == 60
    assert risk.risk_delta > 0
    assert decide(risk, delta) == "REVIEW"
    assert any("authorization removed" in line for line in risk.evidence)


def test_insufficient_evidence_never_softens_a_block() -> None:
    before = [_endpoint(endpoint="/admin/export", resource="Customer", sensitivity="CRITICAL")]
    after = [
        _endpoint(
            endpoint="/admin/export",
            resource="Customer",
            sensitivity="CRITICAL",
            authentication=False,
            required_role=None,
        )
    ]
    risk, delta = _score(before, after)

    assert decide(risk, delta) == "BLOCK"
    assert decide(risk, delta, insufficient=True) == "BLOCK"


def test_insufficient_evidence_still_escalates_an_allow() -> None:
    safe = [_endpoint(endpoint="/ping", resource="Ping", sensitivity="LOW", required_role=None)]
    risk, delta = _score(safe, safe)

    assert decide(risk, delta) == "ALLOW"
    assert decide(risk, delta, insufficient=True) == "REVIEW"


def test_risk_before_is_repository_wide_in_both_scoring_branches() -> None:
    existing = _endpoint(
        endpoint="/a",
        resource="Customer",
        sensitivity="HIGH",
        authentication=False,
        required_role=None,
    )
    added = _endpoint(
        endpoint="/b",
        resource="Audit",
        sensitivity="MEDIUM",
        authentication=False,
        required_role=None,
    )

    with_new_path, _ = _score([existing], [existing, added])
    without_new_path, _ = _score([existing], [existing])

    assert with_new_path.risk_before == without_new_path.risk_before
    # the per-route before-state is still reported per finding
    assert with_new_path.finding_results[0].risk_before == 0


def test_category_for_matches_the_published_bands() -> None:
    for band in BANDS:
        assert category_for(band.minimum) == band.category
        assert category_for(band.maximum) == band.category
