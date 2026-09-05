from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


HttpMethod = Literal["GET", "POST", "PUT", "PATCH", "DELETE"]
Sensitivity = Literal["LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"]
NodeType = Literal[
    "USER", "ROLE", "ENDPOINT", "CONTROLLER", "FUNCTION", "SERVICE",
    "DATABASE", "DATA_RESOURCE", "EXTERNAL_SERVICE",
]
Relationship = Literal[
    "CAN_ACCESS", "CALLS", "READS", "WRITES", "REQUIRES_ROLE",
    "CONNECTS_TO", "RETURNS", "HAS_ROLE",
]
Category = Literal["LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL"]
Verdict = Literal["ALLOW", "REVIEW", "BLOCK"]


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class EndpointIr(ContractModel):
    endpoint: str = Field(min_length=1)
    method: HttpMethod
    controller: str = Field(min_length=1)
    authentication: bool
    required_role: str | None
    service: str | None
    repository: str | None
    resource: str = Field(min_length=1)
    sensitivity: Sensitivity


class AnalysisRequest(ContractModel):
    before: list[EndpointIr]
    after: list[EndpointIr]


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


class AnalysisResult(ContractModel):
    scenario: str
    graph_delta: GraphDelta
    risk_result: RiskResult
    verdict: Verdict
