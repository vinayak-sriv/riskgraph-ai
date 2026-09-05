export type Verdict = "ALLOW" | "REVIEW" | "BLOCK";
export type Category = "LOW" | "MODERATE" | "MEDIUM" | "HIGH" | "CRITICAL";
export type GraphNode = { id: string; node_type: string; name: string };
export type GraphEdge = { source_id: string; target_id: string; relationship: string };
export type SecurityGraph = { nodes: GraphNode[]; edges: GraphEdge[] };
export type PathEvidence = { source: string; target: string; nodes: string[]; edges: string[] };
export type ComponentScore = { score: number; weight: number; weighted_score: number };

export type AnalysisResult = {
  scenario: string;
  verdict: Verdict;
  graph_delta: {
    before: SecurityGraph;
    after: SecurityGraph;
    new_paths: PathEvidence[];
    removed_paths: PathEvidence[];
  };
  risk_result: {
    risk_before: number;
    risk_after: number;
    risk_delta: number;
    category_before: Category;
    category_after: Category;
    components: Record<string, ComponentScore>;
    evidence: string[];
  };
};
