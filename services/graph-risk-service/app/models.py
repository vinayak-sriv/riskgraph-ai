from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

HttpMethod = Literal["GET", "POST", "PUT", "PATCH", "DELETE"]
Sensitivity = Literal["LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"]
NodeType = Literal[
    "USER",
    "ROLE",
    "ENDPOINT",
    "CONTROLLER",
    "FUNCTION",
    "SERVICE",
    "DATABASE",
    "DATA_RESOURCE",
    "EXTERNAL_SERVICE",
]
Relationship = Literal[
    "CAN_ACCESS",
    "CALLS",
    "READS",
    "WRITES",
    "REQUIRES_ROLE",
    "CONNECTS_TO",
    "RETURNS",
    "HAS_ROLE",
]
Category = Literal["LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"]
Verdict = Literal["ALLOW", "REVIEW", "BLOCK"]


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class EndpointIr(ContractModel):
    endpoint: str = Field(min_length=1, max_length=512)
    method: HttpMethod
    controller: str = Field(min_length=1, max_length=512)
    authentication: bool
    required_role: str | None = Field(max_length=256)
    service: str | None = Field(max_length=512)
    repository: str | None = Field(max_length=512)
    resource: str = Field(min_length=1, max_length=512)
    sensitivity: Sensitivity

    @model_validator(mode="after")
    def consistent_auth(self):
        if not self.endpoint.startswith("/") or (not self.authentication and self.required_role):
            raise ValueError("Route must be absolute and public endpoints cannot require a role")
        return self


class Quality(ContractModel):
    confidence: Literal["LOW", "MEDIUM", "HIGH"] = "HIGH"
    coverage_ratio: float = Field(default=1, ge=0, le=1)
    incomplete: bool = False


class AnalysisRequest(ContractModel):
    before: list[EndpointIr] = Field(max_length=2000)
    after: list[EndpointIr] = Field(max_length=2000)
    quality: Quality = Field(default_factory=Quality)

    @model_validator(mode="after")
    def unambiguous_routes(self):
        for rows in (self.before, self.after):
            seen = {}
            for row in rows:
                key = (row.method, row.endpoint)
                auth = (row.authentication, row.required_role)
                if key in seen and seen[key] != auth:
                    raise ValueError("Conflicting authorization for the same route")
                seen[key] = auth
        return self


class Node(ContractModel):
    id: str
    node_type: NodeType
    name: str


class Edge(ContractModel):
    source_id: str
    target_id: str
    relationship: Relationship


class SecurityGraph(ContractModel):
    nodes: list[Node]
    edges: list[Edge]


class PathEvidence(ContractModel):
    source: str
    target: str
    nodes: list[str]
    edges: list[str]


class GraphDelta(ContractModel):
    before: SecurityGraph
    after: SecurityGraph
    new_paths: list[PathEvidence]
    removed_paths: list[PathEvidence]


class RiskScoreRequest(ContractModel):
    before: list[EndpointIr]
    after: list[EndpointIr]
    graph_delta: GraphDelta


class ComponentScore(ContractModel):
    score: int = Field(ge=0, le=100)
    weight: float = Field(ge=0, le=1)
    weighted_score: float = Field(ge=0, le=100)


class RiskComponents(ContractModel):
    reachability: ComponentScore
    authorization_change: ComponentScore
    data_sensitivity: ComponentScore
    external_exposure: ComponentScore
    privilege_impact: ComponentScore
    exploitability: ComponentScore


class RiskResult(ContractModel):
    risk_before: int = Field(ge=0, le=100)
    risk_after: int = Field(ge=0, le=100)
    risk_delta: int = Field(ge=-100, le=100)
    category_before: Category
    category_after: Category
    components: RiskComponents
    evidence: list[str]
    components_before: RiskComponents | None = None
    policy_version: str = "1.0.0"


class AnalysisResult(ContractModel):
    scenario: str
    graph_delta: GraphDelta
    risk_result: RiskResult
    verdict: Verdict
    schema_version: str = "1.1.0"
    reason_codes: list[str] = Field(default_factory=list)
    quality: Quality = Field(default_factory=Quality)
