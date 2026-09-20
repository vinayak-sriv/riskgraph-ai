from dataclasses import dataclass

from .graph_engine import endpoint_node_id, resource_node_id
from .models import (
    Category,
    ComponentScore,
    EndpointIr,
    FindingRiskResult,
    GraphDelta,
    RiskBand,
    RiskComponents,
    RiskPolicyMetadata,
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


RiskCandidate = tuple[str, str, RawScores, RawScores]


def score_risk(
    before: list[EndpointIr],
    after: list[EndpointIr],
    graph_delta: GraphDelta,
) -> RiskResult:
    removed_auth = _authorization_removals(before, after)
    affected_routes = {
        (path.nodes[1], path.target) for path in graph_delta.new_paths if len(path.nodes) > 1
    }
    finding_results: list[FindingRiskResult] = []
    if affected_routes:
        before_by_route_resource: dict[tuple[str, str], list[EndpointIr]] = {}
        after_by_route_resource: dict[tuple[str, str], list[EndpointIr]] = {}
        for endpoint in before:
            key = (endpoint_node_id(endpoint), resource_node_id(endpoint.resource))
            before_by_route_resource.setdefault(key, []).append(endpoint)
        for endpoint in after:
            key = (endpoint_node_id(endpoint), resource_node_id(endpoint.resource))
            after_by_route_resource.setdefault(key, []).append(endpoint)

        candidates: list[RiskCandidate] = []
        for route_id, resource_id in sorted(affected_routes):
            current = after_by_route_resource.get((route_id, resource_id), [])
            previous = before_by_route_resource.get((route_id, resource_id), [])
            route_removal = {route_id} if route_id in removed_auth else set()
            before_state = _state_scores(previous, _has_public_sensitive_endpoint(previous))
            after_state = _state_scores(current, True, route_removal)
            candidates.append(
                (
                    route_id,
                    resource_id,
                    before_state,
                    after_state,
                )
            )
            risk_before = _weighted_total(before_state)
            risk_after = _weighted_total(after_state)
            evidence = []
            if route_removal and previous and current:
                role = previous[0].required_role or "authenticated-user"
                evidence.append(
                    f"{role} authorization removed from {current[0].method} {current[0].endpoint}"
                )
            evidence.append(
                "Anonymous user can newly reach sensitive resource "
                + resource_id.removeprefix("resource:")
            )
            finding_results.append(
                FindingRiskResult(
                    route_id=route_id,
                    resource_id=resource_id,
                    risk_before=risk_before,
                    risk_after=risk_after,
                    risk_delta=risk_after - risk_before,
                    category_before=category_for(risk_before),
                    category_after=category_for(risk_after),
                    components=_components(after_state),
                    components_before=_components(before_state),
                    evidence=evidence,
                    policy_version=POLICY.version,
                )
            )
        _, _, _, after_scores = max(candidates, key=_candidate_rank)
        # risk_before/risk_after are repository-wide by definition; the winning
        # route's own before-state stays in finding_results[].risk_before.
        before_scores = _highest_state(before)
        risk_before = _weighted_total(before_scores)
        risk_after = _weighted_total(after_scores)
    else:
        before_scores = _highest_state(before)
        after_scores = _highest_state(after, removed_auth)
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
        policy=_policy_metadata(),
        finding_results=finding_results,
    )


def decide(risk: RiskResult, graph_delta: GraphDelta, insufficient: bool = False) -> Verdict:
    # Incomplete evidence is an escalation floor, never a ceiling: it can lift
    # ALLOW to REVIEW but must not soften a BLOCK, or an unrelated unresolved
    # authorization delta would be enough to unblock an exposed endpoint.
    if graph_delta.new_paths and risk.risk_after >= POLICY.block_after:
        return "BLOCK"
    if (
        risk.risk_after >= POLICY.review_after
        or risk.risk_delta >= POLICY.review_delta
        or insufficient
    ):
        return "REVIEW"
    return "ALLOW"


def reason_codes(
    risk: RiskResult,
    delta: GraphDelta,
    insufficient: bool,
    unresolved_authorization_delta: bool = False,
) -> list[str]:
    reasons = []
    if delta.new_paths:
        reasons.append("NEW_ANONYMOUS_SENSITIVE_PATH")
    if delta.removed_paths:
        reasons.append("ANONYMOUS_SENSITIVE_PATH_REMOVED")
    if risk.risk_after >= POLICY.review_after:
        reasons.append("RISK_AFTER_REVIEW_THRESHOLD")
    if risk.risk_delta >= POLICY.review_delta:
        reasons.append("RISK_DELTA_REVIEW_THRESHOLD")
    if unresolved_authorization_delta:
        reasons.append("UNRESOLVED_AUTHORIZATION_DELTA")
    if insufficient:
        reasons.append("INSUFFICIENT_EXTRACTION_EVIDENCE")
    return reasons or ["NO_REVIEW_CONDITION"]


BANDS = [
    RiskBand(category="LOW", minimum=0, maximum=20),
    RiskBand(category="MODERATE", minimum=21, maximum=40),
    RiskBand(category="MEDIUM", minimum=41, maximum=60),
    RiskBand(category="HIGH", minimum=61, maximum=80),
    RiskBand(category="CRITICAL", minimum=81, maximum=100),
]


def category_for(score: int) -> Category:
    """Derived from BANDS so the published rubric cannot disagree with it."""
    return next((band.category for band in BANDS if score <= band.maximum), BANDS[-1].category)


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


def _highest_state(
    endpoints: list[EndpointIr],
    removed_auth: set[str] | None = None,
) -> RawScores:
    """Return one coherent route state instead of mixing component maxima.

    ``removed_auth`` must be threaded through: without it an authorization
    removal on a LOW/MODERATE resource scores zero, because such a route never
    produces a new anonymous->sensitive path and so never reaches the
    per-route scoring branch in ``score_risk``.
    """
    removed_auth = removed_auth or set()
    if not endpoints:
        return _state_scores([], False)
    candidates = [
        _state_scores(
            [endpoint],
            _has_public_sensitive_endpoint([endpoint]),
            {endpoint_node_id(endpoint)} & removed_auth,
        )
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
    total: float = sum(getattr(scores, name) * weight for name, weight in WEIGHTS.items())
    return round(total)


def _candidate_rank(candidate: RiskCandidate) -> tuple[int, int, str, str]:
    """Rank affected route/resource states without ordering domain objects.

    Post-change impact is primary. An equal impact selects the largest increase;
    stable route/resource identities are deterministic final tie breakers.
    """
    route_id, resource_id, before_scores, after_scores = candidate
    risk_before = _weighted_total(before_scores)
    risk_after = _weighted_total(after_scores)
    return risk_after, risk_after - risk_before, route_id, resource_id


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


def _policy_metadata() -> RiskPolicyMetadata:
    return RiskPolicyMetadata(
        version=POLICY.version,
        weights=POLICY.weights,
        review_after=POLICY.review_after,
        block_after=POLICY.block_after,
        review_delta=POLICY.review_delta,
        bands=list(BANDS),
    )


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
