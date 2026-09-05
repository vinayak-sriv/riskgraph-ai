from app.graph_engine import compare_graphs
from app.models import AnalysisRequest
from app.risk_engine import decide, score_risk


def test_authorization_removal_creates_exact_bfs_path(authorization_removal_payload: dict) -> None:
    request = AnalysisRequest.model_validate(authorization_removal_payload)
    delta = compare_graphs(request.before, request.after)

    assert len(delta.new_paths) == 1
    assert delta.removed_paths == []
    assert delta.new_paths[0].nodes == [
        "user:anonymous",
        "endpoint:GET:/admin/export",
        "controller:AdminExportController",
        "service:AdminExportService",
        "repository:CustomerRepository",
        "resource:Customer",
    ]
    assert delta.new_paths[0].edges == ["CAN_ACCESS", "CALLS", "CALLS", "READS", "RETURNS"]


def test_protected_graph_has_role_gate_and_no_anonymous_access(
    authorization_removal_payload: dict,
) -> None:
    request = AnalysisRequest.model_validate(authorization_removal_payload)
    delta = compare_graphs(request.before, request.after)
    before_edges = {
        (edge.source_id, edge.target_id, edge.relationship) for edge in delta.before.edges
    }

    assert ("endpoint:GET:/admin/export", "role:ADMIN", "REQUIRES_ROLE") in before_edges
    assert not any(
        edge.source_id == "user:anonymous" and edge.relationship == "CAN_ACCESS"
        for edge in delta.before.edges
    )


def test_risk_formula_matches_roadmap_and_blocks(authorization_removal_payload: dict) -> None:
    request = AnalysisRequest.model_validate(authorization_removal_payload)
    delta = compare_graphs(request.before, request.after)
    risk = score_risk(request.before, request.after, delta)

    assert (risk.risk_before, risk.risk_after, risk.risk_delta) == (22, 91, 69)
    assert (risk.category_before, risk.category_after) == ("MODERATE", "CRITICAL")
    assert sum(
        component["weighted_score"] for component in risk.components.model_dump().values()
    ) == 91
    assert decide(risk, delta) == "BLOCK"
    assert risk.evidence == [
        "ADMIN authorization removed from GET /admin/export",
        "Anonymous user can newly reach sensitive resource Customer",
    ]


def test_unchanged_protected_endpoint_is_allowed(authorization_removal_payload: dict) -> None:
    request = AnalysisRequest.model_validate(
        {
            "before": authorization_removal_payload["before"],
            "after": authorization_removal_payload["before"],
        }
    )
    delta = compare_graphs(request.before, request.after)
    risk = score_risk(request.before, request.after, delta)

    assert delta.new_paths == []
    assert (risk.risk_before, risk.risk_after, risk.risk_delta) == (22, 22, 0)
    assert decide(risk, delta) == "ALLOW"


def test_low_sensitivity_public_resource_is_not_a_sensitive_path() -> None:
    endpoint = {
        "endpoint": "/status",
        "method": "GET",
        "controller": "StatusController",
        "authentication": False,
        "required_role": None,
        "service": None,
        "repository": None,
        "resource": "Status",
        "sensitivity": "LOW",
    }
    request = AnalysisRequest.model_validate({"before": [], "after": [endpoint]})
    delta = compare_graphs(request.before, request.after)

    assert delta.new_paths == []
