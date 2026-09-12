import type {
  AnalysisResult,
  Category,
  GraphNode,
  SecurityGraph,
  SourceEvidenceRow,
} from "./types";
export type Theme = "light" | "dark";
export type WorkspaceView =
  "analysis" | "new-analysis" | "saved-scans" | "account";
export type EvidenceCertainty =
  "CONFIRMED" | "INFERRED" | "AMBIGUOUS" | "UNRESOLVED";
export type SelectedNode = {
  node: GraphNode;
  graph: SecurityGraph;
  revision: "Before" | "After";
  isPathNode: boolean;
};

export function formatLabel(value: string) {
  return value
    .split("_")
    .map((word) =>
      word ? word[0].toUpperCase() + word.slice(1).toLowerCase() : "",
    )
    .join(" ");
}

export function riskTone(category: Category): "safe" | "warning" | "danger" {
  return category === "HIGH" || category === "CRITICAL"
    ? "danger"
    : category === "LOW"
      ? "safe"
      : "warning";
}

export function evidenceCertainty(analysis: AnalysisResult): EvidenceCertainty {
  if (analysis.validation_status === "CONFIRMED") return "CONFIRMED";
  if (analysis.quality?.incomplete || analysis.quality?.confidence === "LOW")
    return "AMBIGUOUS";
  return "INFERRED";
}

export function certaintyDescription(certainty: EvidenceCertainty) {
  if (certainty === "CONFIRMED")
    return "Runtime validation confirmed the static finding in the registered Docker sandbox.";
  if (certainty === "AMBIGUOUS")
    return "Static evidence exists, but extraction coverage or confidence is incomplete.";
  if (certainty === "UNRESOLVED")
    return "The analyzer could not resolve this item; it is a diagnostic, not supporting evidence.";
  return "Deterministic static analysis supports this structural finding; runtime validation has not confirmed exploitability.";
}

export function nodesForEvidence(analysis: AnalysisResult, evidence: string) {
  const normalized = evidence.toLowerCase();
  return analysis.graph_delta.after.nodes.filter((node) => {
    const name = node.name.toLowerCase();
    const endpointPath =
      node.node_type === "ENDPOINT" ? name.split(" ").slice(1).join(" ") : "";
    return (
      (name.length >= 4 && normalized.includes(name)) ||
      (endpointPath.length >= 4 && normalized.includes(endpointPath))
    );
  });
}

export function sourceEvidenceForNode(
  analysis: AnalysisResult,
  node: GraphNode,
): Array<{ revision: string; row: SourceEvidenceRow }> {
  if (node.node_type !== "ENDPOINT") return [];
  const [method, ...pathParts] = node.name.split(" ");
  const endpoint = pathParts.join(" ");
  return Object.entries(analysis.source_evidence ?? {}).flatMap(
    ([revision, rows]) =>
      rows
        .filter(
          (row) =>
            row.endpoint.method.toUpperCase() === method.toUpperCase() &&
            row.endpoint.endpoint === endpoint,
        )
        .map((row) => ({ revision, row })),
  );
}
