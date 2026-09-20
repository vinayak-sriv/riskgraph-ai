from fastapi import Depends, FastAPI, HTTPException

from .envelope import build_envelope
from .models import AnalyzeRequest
from .repository_access import RepositoryNotAllowed, validate_repository_path
from .service_auth import require_service_token

app = FastAPI(title="RiskGraph Python Analyzer", version="0.2.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "python-analyzer"}


@app.post("/analyze", dependencies=[Depends(require_service_token)])
def analyze(request: AnalyzeRequest) -> dict:
    try:
        repository_path = validate_repository_path(request.repository_path)
    except RepositoryNotAllowed as error:
        status = 503 if error.code == "REPOSITORY_ROOTS_NOT_CONFIGURED" else 403
        raise HTTPException(status_code=status, detail=error.code) from error
    return build_envelope(repository_path, request.old_commit, request.new_commit)
