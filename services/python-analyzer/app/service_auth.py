import hmac
import os
from typing import Annotated

from fastapi import HTTPException, Security
from fastapi.security import APIKeyHeader

# Shares the analyzer-role secret with java-analyzer (platform-api's AnalysisClient
# sends the same token to any /analyze route, regardless of which analyzer it targets).
SERVICE_TOKEN = APIKeyHeader(name="X-RiskGraph-Service-Token", auto_error=False)


def require_service_token(
    supplied: Annotated[str | None, Security(SERVICE_TOKEN)],
) -> None:
    expected = os.environ.get("RISKGRAPH_ANALYZER_SERVICE_TOKEN", "")
    if not expected:
        raise HTTPException(
            status_code=503, detail="Internal service authentication is not configured"
        )
    if supplied is None or not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Invalid internal service credential")
