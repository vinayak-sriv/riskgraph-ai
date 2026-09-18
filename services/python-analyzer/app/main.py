from fastapi import Depends, FastAPI

from .envelope import build_envelope
from .models import AnalyzeRequest
from .service_auth import require_service_token

app = FastAPI(title="RiskGraph Python Analyzer", version="0.2.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "python-analyzer"}


@app.post("/analyze", dependencies=[Depends(require_service_token)])
def analyze(request: AnalyzeRequest) -> dict:
    return build_envelope(request.repository_path, request.old_commit, request.new_commit)
