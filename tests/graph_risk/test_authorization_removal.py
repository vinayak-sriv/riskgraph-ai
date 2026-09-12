from app.graph_engine import compare_graphs, scoped_id
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
        scoped_id("controller", "AdminExportController", request.after[0]),
        scoped_id("service", "AdminExportService", request.after[0]),
        scoped_id("repository", "CustomerRepository", request.after[0]),
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
    assert (
        sum(component["weighted_score"] for component in risk.components.model_dump().values())
        == 91
    )
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


def test_unrelated_authenticated_critical_resource_cannot_change_path_score() -> None:
    exposed = {
        "endpoint": "/reports",
        "method": "GET",
        "controller": "ReportController",
        "authentication": False,
        "required_role": None,
        "service": "ReportService",
        "repository": "ReportRepository",
        "resource": "Report",
        "sensitivity": "HIGH",
    }
    unrelated = {
        "endpoint": "/vault",
        "method": "GET",
        "controller": "VaultController",
        "authentication": True,
        "required_role": "ADMIN",
        "service": "VaultService",
        "repository": "VaultRepository",
        "resource": "Vault",
        "sensitivity": "CRITICAL",
    }
    without_unrelated = AnalysisRequest.model_validate({"before": [], "after": [exposed]})
    with_unrelated = AnalysisRequest.model_validate(
        {"before": [unrelated], "after": [exposed, unrelated]}
    )

    first_delta = compare_graphs(without_unrelated.before, without_unrelated.after)
    second_delta = compare_graphs(with_unrelated.before, with_unrelated.after)
    first = score_risk(without_unrelated.before, without_unrelated.after, first_delta)
    second = score_risk(with_unrelated.before, with_unrelated.after, second_delta)

    assert first.risk_after == second.risk_after
    assert first.components.data_sensitivity.score == 80
    assert second.components.data_sensitivity.score == 80


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


def test_implementation_rename_is_not_a_new_security_path() -> None:
    before = {
        "endpoint": "/customers",
        "method": "GET",
        "controller": "OldController",
        "authentication": False,
        "required_role": None,
        "service": "OldService",
        "repository": "OldRepository",
        "resource": "Customer",
        "sensitivity": "HIGH",
    }
    after = {
        **before,
        "controller": "NewController",
        "service": "NewService",
        "repository": "NewRepository",
    }
    request = AnalysisRequest.model_validate({"before": [before], "after": [after]})
    delta = compare_graphs(request.before, request.after)

    assert delta.new_paths == []
    assert delta.removed_paths == []


def test_sensitivity_does_not_leak_between_routes_sharing_a_resource() -> None:
    public_low = {
        "endpoint": "/catalog",
        "method": "GET",
        "controller": "CatalogController",
        "authentication": False,
        "required_role": None,
        "service": None,
        "repository": None,
        "resource": "Customer",
        "sensitivity": "LOW",
    }
    protected_high = {
        **public_low,
        "endpoint": "/admin/customers",
        "authentication": True,
        "required_role": "ADMIN",
        "sensitivity": "HIGH",
    }
    request = AnalysisRequest.model_validate({"before": [], "after": [public_low, protected_high]})

    assert compare_graphs(request.before, request.after).new_paths == []


def test_unrelated_auth_removal_does_not_raise_another_findings_score() -> None:
    status_before = {
        "endpoint": "/status",
        "method": "GET",
        "controller": "StatusController",
        "authentication": True,
        "required_role": "ADMIN",
        "service": None,
        "repository": None,
        "resource": "Status",
        "sensitivity": "LOW",
    }
    status_after = {**status_before, "authentication": False, "required_role": None}
    payment = {
        "endpoint": "/payments",
        "method": "POST",
        "controller": "PaymentController",
        "authentication": False,
        "required_role": None,
        "service": "PaymentService",
        "repository": "PaymentRepository",
        "resource": "Payment",
        "sensitivity": "CRITICAL",
    }
    isolated = AnalysisRequest.model_validate({"before": [], "after": [payment]})
    combined = AnalysisRequest.model_validate(
        {"before": [status_before], "after": [status_after, payment]}
    )
    isolated_delta = compare_graphs(isolated.before, isolated.after)
    combined_delta = compare_graphs(combined.before, combined.after)

    assert (
        score_risk(combined.before, combined.after, combined_delta).risk_after
        == score_risk(isolated.before, isolated.after, isolated_delta).risk_after
        == 60
    )
