from dataclasses import dataclass

from .graph_engine import endpoint_node_id, resource_node_id
from .models import (
    ComponentScore,
    EndpointIr,
    GraphDelta,
    RiskComponents,
    RiskResult,
    Verdict,
)
from .policy import POLICY

WEIGHTS = POLICY.weights
SENSITIVITY_SCORES = {
    "LOW": 20,
    "MODERATE": 40,
    "MEDIUM": 60,
    "HIGH": 80,
    "CRITICAL": 100,
}


@dataclass(frozen=True)
class RawScores:
    reachability: int
    authorization_change: int
    data_sensitivity: int
    external_exposure: int
    privilege_impact: int
    exploitability: int


def score_risk(
    before: list[EndpointIr],
    after: list[EndpointIr],
    graph_delta: GraphDelta,
) -> RiskResult:
    removed_auth = _authorization_removals(before, after)
    affected_routes = {
        (path.nodes[1], path.target) for path in graph_delta.new_paths if len(path.nodes) > 1
    }
    if affected_routes:
        before_by_route: dict[str, list[EndpointIr]] = {}
        after_by_route_resource: dict[tuple[str, str], list[EndpointIr]] = {}
        for endpoint in before:
            before_by_route.setdefault(endpoint_node_id(endpoint), []).append(endpoint)
        for endpoint in after:
            key = (endpoint_node_id(endpoint), resource_node_id(endpoint.resource))
            after_by_route_resource.setdefault(key, []).append(endpoint)

        candidates = []
        for route_id, resource_id in sorted(affected_routes):
            current = after_by_route_resource.get((route_id, resource_id), [])
            previous = before_by_route.get(route_id, [])
            route_removal = {route_id} if route_id in removed_auth else set()
            before_state = _state_scores(previous, _has_public_sensitive_endpoint(previous))
            after_state = _state_scores(current, True, route_removal)
            candidates.append(
                (
                    _weighted_total(after_state),
                    _weighted_total(before_state),
                    route_id,
                    before_state,
                    after_state,
                )
            )
        _, risk_before, _, before_scores, after_scores = max(candidates)
        risk_after = _weighted_total(after_scores)
    else:
        before_scores = _highest_state(before)
        after_scores = _highest_state(after)
        risk_before = _weighted_total(before_scores)
        risk_after = _weighted_total(after_scores)
    return RiskResult(
        risk_before=risk_before,
        risk_after=risk_after,
        risk_delta=risk_after - risk_before,
        category_before=category_for(risk_before),
        category_after=category_for(risk_after),
        components=_components(after_scores),
        components_before=_components(before_scores),
        policy_version=POLICY.version,
        evidence=_evidence(before, after, graph_delta, removed_auth),
    )


def decide(risk: RiskResult, graph_delta: GraphDelta, insufficient: bool = False) -> Verdict:
    if insufficient:
        return "REVIEW"
    if graph_delta.new_paths and risk.risk_after >= POLICY.block_after:
        return "BLOCK"
    if risk.risk_after >= POLICY.review_after or risk.risk_delta >= POLICY.review_delta:
        return "REVIEW"
    return "ALLOW"


def reason_codes(risk: RiskResult, delta: GraphDelta, insufficient: bool) -> list[str]:
    reasons = []
    if delta.new_paths:
        reasons.append("NEW_ANONYMOUS_SENSITIVE_PATH")
    if delta.removed_paths:
        reasons.append("ANONYMOUS_SENSITIVE_PATH_REMOVED")
    if risk.risk_after >= POLICY.review_after:
        reasons.append("RISK_AFTER_REVIEW_THRESHOLD")
    if risk.risk_delta >= POLICY.review_delta:
        reasons.append("RISK_DELTA_REVIEW_THRESHOLD")
    if insufficient:
        reasons.append("INSUFFICIENT_EXTRACTION_EVIDENCE")
    return reasons or ["NO_REVIEW_CONDITION"]


def category_for(score: int) -> str:
    if score <= 20:
        return "LOW"
    if score <= 40:
        return "MODERATE"
    if score <= 60:
        return "MEDIUM"
    if score <= 80:
        return "HIGH"
    return "CRITICAL"


def _state_scores(
    endpoints: list[EndpointIr],
    has_anonymous_path: bool,
    removed_auth: set[str] | None = None,
) -> RawScores:
    removed_auth = removed_auth or set()
    max_sensitivity = max(
        (SENSITIVITY_SCORES[endpoint.sensitivity] for endpoint in endpoints),
        default=0,
    )
    public_sensitive = _has_public_sensitive_endpoint(endpoints)
    has_sensitive_get = any(
        endpoint.method == "GET" and SENSITIVITY_SCORES[endpoint.sensitivity] >= 60
        for endpoint in endpoints
    )
    # Fixed MVP rubric: auth removal has broad privilege impact (60), while an
    # unauthenticated sensitive GET is directly testable over HTTP (50).
    exploitability = (
        50 if public_sensitive and has_sensitive_get else 20 if has_sensitive_get else 0
    )
    return RawScores(
        reachability=100 if has_anonymous_path else 0,
        authorization_change=100 if removed_auth else 0,
        data_sensitivity=max_sensitivity,
        external_exposure=100 if public_sensitive else 0,
        privilege_impact=60 if removed_auth else 0,
        exploitability=exploitability,
    )


def _highest_state(endpoints: list[EndpointIr]) -> RawScores:
    """Return one coherent route state instead of mixing component maxima."""
    if not endpoints:
        return _state_scores([], False)
    candidates = [
        _state_scores([endpoint], _has_public_sensitive_endpoint([endpoint]))
        for endpoint in endpoints
    ]
    return max(candidates, key=_weighted_total)


def _authorization_removals(before: list[EndpointIr], after: list[EndpointIr]) -> set[str]:
    before_by_endpoint = {endpoint_node_id(endpoint): endpoint for endpoint in before}
    return {
        endpoint_node_id(endpoint)
        for endpoint in after
        if (previous := before_by_endpoint.get(endpoint_node_id(endpoint)))
        and previous.authentication
        and not endpoint.authentication
    }


def _has_public_sensitive_endpoint(endpoints: list[EndpointIr]) -> bool:
    return any(
        not endpoint.authentication and SENSITIVITY_SCORES[endpoint.sensitivity] >= 60
        for endpoint in endpoints
    )


def _weighted_total(scores: RawScores) -> int:
    return round(sum(getattr(scores, name) * weight for name, weight in WEIGHTS.items()))


def _components(scores: RawScores) -> RiskComponents:
    values = {
        name: ComponentScore(
            score=getattr(scores, name),
            weight=weight,
            weighted_score=getattr(scores, name) * weight,
        )
        for name, weight in WEIGHTS.items()
    }
    return RiskComponents(**values)


def _evidence(
    before: list[EndpointIr],
    after: list[EndpointIr],
    graph_delta: GraphDelta,
    removed_auth: set[str],
) -> list[str]:
    evidence = []
    before_by_id = {endpoint_node_id(endpoint): endpoint for endpoint in before}
    after_by_id = {endpoint_node_id(endpoint): endpoint for endpoint in after}
    for endpoint_id in sorted(removed_auth):
        previous = before_by_id[endpoint_id]
        current = after_by_id[endpoint_id]
        role = previous.required_role or "authenticated-user"
        evidence.append(f"{role} authorization removed from {current.method} {current.endpoint}")
    for path in graph_delta.new_paths:
        resource = path.target.removeprefix("resource:")
        evidence.append(f"Anonymous user can newly reach sensitive resource {resource}")
    if not evidence:
        evidence.append("No new anonymous-to-sensitive-resource path detected")
    return evidence
