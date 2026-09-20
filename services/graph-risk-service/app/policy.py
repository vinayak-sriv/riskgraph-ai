"""Versioned deterministic policy. Invalid configuration fails startup closed."""

import os
from pathlib import Path
from typing import Literal

import yaml  # type: ignore[import-untyped]
from pydantic import BaseModel, ConfigDict, Field, model_validator

WEIGHTS = dict(
    reachability=0.25,
    authorization_change=0.20,
    data_sensitivity=0.20,
    external_exposure=0.15,
    privilege_impact=0.10,
    exploitability=0.10,
)


class Policy(BaseModel):
    model_config = ConfigDict(extra="forbid")
    version: Literal["1.0.0"]
    weights: dict[str, float] = Field(json_schema_extra={"const": dict(WEIGHTS)})
    block_after: int = Field(ge=61, le=61)
    review_after: int = Field(ge=41, le=41)
    review_delta: int = Field(ge=21, le=21)

    @model_validator(mode="after")
    def fixed_formula(self) -> "Policy":
        if self.weights != WEIGHTS:
            raise ValueError("Policy v1 must preserve the documented six-component formula")
        return self


POLICY = Policy.model_validate(
    yaml.safe_load(
        Path(
            os.environ.get("RISKGRAPH_RISK_POLICY", str(Path(__file__).with_name("policy-v1.yml")))
        ).read_text(encoding="utf-8")
    )
)
