export type Verdict = "ALLOW" | "REVIEW" | "BLOCK";
export type Category = "LOW" | "MODERATE" | "MEDIUM" | "HIGH" | "CRITICAL";
export type GraphNode = { id: string; node_type: string; name: string };
export type GraphEdge = {
  source_id: string;
  target_id: string;
  relationship: string;
};
export type SecurityGraph = { nodes: GraphNode[]; edges: GraphEdge[] };
export type PathEvidence = {
  source: string;
  target: string;
  nodes: string[];
  edges: string[];
};
export type ComponentScore = {
  score: number;
  weight: number;
  weighted_score: number;
};
export type SourceEvidenceRow = {
  source_location: { path: string; start_line: number; end_line: number };
  endpoint: { endpoint: string; method: string };
  extraction_confidence: { overall: string };
};
export type Diagnostic = {
  code: string;
  severity: string;
  message: string;
  path?: string;
};

export type AnalysisResult = {
  schema_version?: string;
  analyzer_version?: string;
  analyzer_config_hash?: string;
  mode?: string;
  scan_id?: string;
  status?: string;
  pre_validation_verdict?: Verdict;
  final_verdict?: Verdict;
  validation_status?: string;
  validation?: {
    status: string;
    reason_code: string;
    source_commit?: string;
    cleanup_complete?: boolean;
  };
  quality?: { confidence: string; coverage_ratio: number; incomplete: boolean };
  provenance?: {
    repository_identity: string;
    repository_path: string;
    old_commit: string;
    new_commit: string;
  };
  diagnostics?: Diagnostic[];
  source_evidence?: Record<string, SourceEvidenceRow[]>;
  ai?: {
    status: string;
    analysis: {
      finding: string;
      hypothesis: string;
      recommended_test: string;
      confidence: string;
    };
  };
  reason_codes?: string[];
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
    components_before?: Record<string, ComponentScore>;
    policy_version?: string;
    evidence: string[];
  };
};
