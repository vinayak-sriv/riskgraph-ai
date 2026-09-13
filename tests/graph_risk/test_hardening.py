import itertools
import time

import app.graph_engine as graph_engine
import app.risk_engine as risk_engine
import pytest
from app.graph_engine import anonymous_sensitive_paths, compare_graphs
from app.main import analyze
from app.models import AnalysisRequest
from app.policy import POLICY, Policy
from pydantic import ValidationError


def test_same_resource_new_route_is_not_lost(authorization_removal_payload):
    public = authorization_removal_payload["after"][0]
    second = {**public, "endpoint": "/second"}
    request = AnalysisRequest.model_validate({"before": [public], "after": [public, second]})
    delta = compare_graphs(request.before, request.after)
    assert len(delta.new_paths) == 1
    assert delta.new_paths[0].nodes[1] == "endpoint:GET:/second"
    reverse = compare_graphs(request.after, request.before)
    assert reverse.removed_paths == delta.new_paths


def test_shared_controller_does_not_leak_private_call_chain(authorization_removal_payload):
    private = authorization_removal_payload["before"][0]
    public = {
        **private,
        "endpoint": "/status",
        "authentication": False,
        "required_role": None,
        "resource": "Status",
        "sensitivity": "LOW",
        "service": None,
        "repository": None,
    }
    request = AnalysisRequest.model_validate({"before": [], "after": [private, public]})
    delta = compare_graphs(request.before, request.after)
    assert anonymous_sensitive_paths(delta.after, request.after) == {}


def test_order_and_repeated_inputs_are_deterministic(authorization_removal_payload):
    rows = [
        authorization_removal_payload["after"][0],
        {**authorization_removal_payload["after"][0], "endpoint": "/second"},
    ]
    results = [
        analyze(
            AnalysisRequest.model_validate({"before": [], "after": list(order)})
        ).model_dump_json()
        for order in itertools.permutations(rows)
    ]
    assert len(set(results)) == 1


@pytest.mark.parametrize(
    "quality", [{"incomplete": True}, {"confidence": "LOW"}, {"coverage_ratio": 0.4}]
)
def test_insufficient_empty_analysis_never_allows(quality):
    result = analyze(AnalysisRequest(before=[], after=[], quality=quality))
    assert result.verdict == "REVIEW"
    assert "INSUFFICIENT_EXTRACTION_EVIDENCE" in result.reason_codes


def test_policy_rejects_drift():
    with pytest.raises(ValidationError):
        Policy.model_validate({**POLICY.model_dump(), "weights": {"reachability": 1}})


def test_conflicting_route_auth_rejected(authorization_removal_payload):
    with pytest.raises(ValidationError):
        AnalysisRequest(
            before=[],
            after=authorization_removal_payload["before"] + authorization_removal_payload["after"],
        )


def test_analysis_reuses_reachability_results(monkeypatch, authorization_removal_payload):
    calls = 0
    original = graph_engine.anonymous_sensitive_paths

    def counted(*args, **kwargs):
        nonlocal calls
        calls += 1
        return original(*args, **kwargs)

    monkeypatch.setattr(graph_engine, "anonymous_sensitive_paths", counted)
    analyze(AnalysisRequest.model_validate(authorization_removal_payload))
    assert calls == 2


def test_maximum_contract_graph_does_not_regress_to_quadratic_runtime():
    rows = [
        {
            "endpoint": f"/bulk/{index}",
            "method": "GET",
            "controller": f"Controller{index}",
            "authentication": False,
            "required_role": None,
            "service": f"Service{index}",
            "repository": f"Repository{index}",
            "resource": f"Resource{index}",
            "sensitivity": "HIGH",
        }
        for index in range(2_000)
    ]
    request = AnalysisRequest.model_validate({"before": [], "after": rows})
    started = time.perf_counter()
    delta = compare_graphs(request.before, request.after)
    elapsed = time.perf_counter() - started

    assert len(delta.new_paths) == 2_000
    assert elapsed < 2.5

    endpoint_id_calls = 0
    original = risk_engine.endpoint_node_id

    def counted(endpoint):
        nonlocal endpoint_id_calls
        endpoint_id_calls += 1
        return original(endpoint)

    risk_engine.endpoint_node_id = counted
    try:
        risk_engine.score_risk(request.before, request.after, delta)
    finally:
        risk_engine.endpoint_node_id = original
    assert endpoint_id_calls <= 4 * len(rows)
