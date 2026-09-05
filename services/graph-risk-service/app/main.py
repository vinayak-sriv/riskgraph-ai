from fastapi import FastAPI
from .graph_engine import compare_graphs
from .models import AnalysisRequest, AnalysisResult, GraphDelta, RiskResult, RiskScoreRequest
from .risk_engine import decide, score_risk


app = FastAPI(title="RiskGraph Graph Risk Service", version="0.2.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "graph-risk-service"}


@app.post("/graph/delta", response_model=GraphDelta)
def graph_delta(request: AnalysisRequest) -> GraphDelta:
    return compare_graphs(request.before, request.after)


@app.post("/risk/score", response_model=RiskResult)
def risk_score(request: RiskScoreRequest) -> RiskResult:
    return score_risk(request.before, request.after, request.graph_delta)


@app.post("/analysis", response_model=AnalysisResult)
def analyze(request: AnalysisRequest) -> AnalysisResult:
    delta = compare_graphs(request.before, request.after)
    risk = score_risk(request.before, request.after, delta)
    return AnalysisResult(
        scenario="authorization-removal",
        graph_delta=delta,
        risk_result=risk,
        verdict=decide(risk, delta),
    )
