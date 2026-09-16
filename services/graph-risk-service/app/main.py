from fastapi import Depends, FastAPI

from .authorization_policy import unresolved_authorization_deltas
from .graph_engine import compare_graphs
from .models import (
    AnalysisRequest,
    AnalysisResult,
    GraphDelta,
    Quality,
    RiskResult,
    RiskScoreRequest,
)
from .risk_engine import decide, reason_codes, score_risk
from .service_auth import require_service_token

app = FastAPI(title="RiskGraph Graph Risk Service", version="0.2.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "graph-risk-service"}


@app.post("/graph/delta", response_model=GraphDelta, dependencies=[Depends(require_service_token)])
def graph_delta(request: AnalysisRequest) -> GraphDelta:
    return compare_graphs(request.before, request.after)


@app.post("/risk/score", response_model=RiskResult, dependencies=[Depends(require_service_token)])
def risk_score(request: RiskScoreRequest) -> RiskResult:
    # Never accept caller-supplied graph facts as authoritative scoring evidence.
    return score_risk(request.before, request.after, compare_graphs(request.before, request.after))


@app.post("/analysis", response_model=AnalysisResult, dependencies=[Depends(require_service_token)])
def analyze(request: AnalysisRequest) -> AnalysisResult:
    delta = compare_graphs(request.before, request.after)
    risk = score_risk(request.before, request.after, delta)
    unresolved_deltas = unresolved_authorization_deltas(request.before, request.after)
    if unresolved_deltas:
        risk = risk.model_copy(
            update={"evidence": [*risk.evidence, *(item.evidence() for item in unresolved_deltas)]}
        )
    insufficient = (
        request.quality.incomplete
        or request.quality.confidence != "HIGH"
        or request.quality.coverage_ratio < 1
        or bool(unresolved_deltas)
    )
    quality = request.quality
    if unresolved_deltas:
        quality = Quality(
            confidence="LOW",
            coverage_ratio=request.quality.coverage_ratio,
            incomplete=True,
        )
    return AnalysisResult(
        scenario="analysis",
        graph_delta=delta,
        risk_result=risk,
        verdict=decide(risk, delta, insufficient),
        reason_codes=reason_codes(
            risk,
            delta,
            insufficient,
            unresolved_authorization_delta=bool(unresolved_deltas),
        ),
        quality=quality,
    )
