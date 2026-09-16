from contextlib import asynccontextmanager
from typing import Literal

from fastapi import Depends, FastAPI
from pydantic import BaseModel, ConfigDict

from .reasoning import EvidenceRequest, Explanation, OllamaProvider, explain
from .service_auth import require_service_token
from .validation import ValidationRequest, ValidationResult, validate

ollama = OllamaProvider()


class HealthResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    status: Literal["ok"]
    service: Literal["ai-validation-service"]


@asynccontextmanager
async def lifespan(_app: FastAPI):
    yield
    await ollama.close()


app = FastAPI(title="RiskGraph AI Validation Service", version="0.1.0", lifespan=lifespan)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="ok", service="ai-validation-service")


@app.post("/ai/analyze", response_model=Explanation, dependencies=[Depends(require_service_token)])
async def analyze(request: EvidenceRequest) -> Explanation:
    return await explain(request, ollama)


@app.post(
    "/ai/test-suggestion", response_model=Explanation, dependencies=[Depends(require_service_token)]
)
async def test_suggestion(request: EvidenceRequest) -> Explanation:
    return await explain(request, ollama)


@app.post(
    "/validation/http",
    response_model=ValidationResult,
    dependencies=[Depends(require_service_token)],
)
async def validate_http(request: ValidationRequest) -> ValidationResult:
    return await validate(request)
