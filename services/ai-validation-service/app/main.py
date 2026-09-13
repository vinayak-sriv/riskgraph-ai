import os
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from .reasoning import EvidenceRequest, Explanation, OllamaProvider, explain
from .service_auth import require_service_token
from .validation import ValidationRequest, ValidationResult, validate

ollama = OllamaProvider()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    yield
    await ollama.close()


app = FastAPI(title="RiskGraph AI Validation Service", version="0.1.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "ai-validation-service",
        "ollama_base_url": os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434"),
        "ollama_model": os.environ.get("OLLAMA_MODEL", "llama3.1:8b"),
    }


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
