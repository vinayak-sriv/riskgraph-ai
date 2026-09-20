import hmac
import os
from typing import Annotated

from fastapi import HTTPException, Security
from fastapi.security import APIKeyHeader

SERVICE_TOKEN = APIKeyHeader(name="X-RiskGraph-Service-Token", auto_error=False)


def require_service_token(
    supplied: Annotated[str | None, Security(SERVICE_TOKEN)],
) -> None:
    expected = os.environ.get("RISKGRAPH_GRAPH_SERVICE_TOKEN", "")
    if not expected:
        raise HTTPException(
            status_code=503, detail="Internal service authentication is not configured"
        )
    # Compare bytes: hmac.compare_digest on str raises TypeError for a
    # non-ASCII header, turning a 401 into a 500 with a traceback.
    if supplied is None or not hmac.compare_digest(
        supplied.encode("utf-8"), expected.encode("utf-8")
    ):
        raise HTTPException(status_code=401, detail="Invalid internal service credential")
