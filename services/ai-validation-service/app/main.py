import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


app = FastAPI(title="RiskGraph AI Validation Service", version="0.1.0")


class EvidenceRequest(BaseModel):
    risk_result: dict
    evidence: list[str]


class HttpValidationTest(BaseModel):
    target_base_url: str
    method: str
    path: str
    auth_context: str
    expected_status: int
    headers: dict[str, str] = Field(default_factory=dict)
    body: dict | list | str | None = None


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "ai-validation-service",
        "ollama_base_url": os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434"),
        "ollama_model": os.environ.get("OLLAMA_MODEL", "llama3.1:8b"),
    }


@app.post("/ai/analyze")
def analyze(_: EvidenceRequest) -> None:
    raise HTTPException(
        status_code=501,
        detail="Week 3 scaffold only: schema-constrained Ollama analysis starts in a later week",
    )


@app.post("/ai/test-suggestion")
def test_suggestion(_: EvidenceRequest) -> None:
    raise HTTPException(
        status_code=501,
        detail="Week 3 scaffold only: Ollama-backed test suggestions start in a later week",
    )


@app.post("/validation/http")
def validate_http(_: HttpValidationTest) -> None:
    raise HTTPException(
        status_code=501,
        detail="Week 3 scaffold only: Docker sandbox validation runner starts in a later week",
    )
