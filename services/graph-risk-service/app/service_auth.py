import hmac
import os
from typing import Annotated

from fastapi import Header, HTTPException


def require_service_token(
    supplied: Annotated[str | None, Header(alias="X-RiskGraph-Service-Token")] = None,
) -> None:
    expected = os.environ.get("RISKGRAPH_SERVICE_TOKEN", "")
    if not expected:
        raise HTTPException(
            status_code=503, detail="Internal service authentication is not configured"
        )
    if supplied is None or not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Invalid internal service credential")
