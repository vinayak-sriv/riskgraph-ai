import React, { useCallback, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

type Verdict = "ALLOW" | "REVIEW" | "BLOCK";
type Category = "LOW" | "MODERATE" | "MEDIUM" | "HIGH" | "CRITICAL";
type GraphNode = { id: string; node_type: string; name: string };
type GraphEdge = { source_id: string; target_id: string; relationship: string };
type SecurityGraph = { nodes: GraphNode[]; edges: GraphEdge[] };
type PathEvidence = { source: string; target: string; nodes: string[]; edges: string[] };
type ComponentScore = { score: number; weight: number; weighted_score: number };
type AnalysisResult = {
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

const platformUrl = import.meta.env.VITE_PLATFORM_API_BASE_URL ?? "http://localhost:8080";

function App() {
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const loadAnalysis = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${platformUrl}/demo/authorization-removal`);
      if (!response.ok) throw new Error(`Platform API returned HTTP ${response.status}`);
      setAnalysis((await response.json()) as AnalysisResult);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to load analysis");
      setAnalysis(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadAnalysis(); }, [loadAnalysis]);

  if (loading) {
    return <StatusScreen title="Running deterministic analysis" detail="Building both security graphs and comparing reachability." />;
  }
  if (error || !analysis) {
    return (
      <StatusScreen title="Analysis unavailable" detail={error ?? "No result was returned."}>
        <button className="retry-button" onClick={() => void loadAnalysis()}>Retry</button>
      </StatusScreen>
    );
  }

  const risk = analysis.risk_result;
  const newPath = analysis.graph_delta.new_paths[0];
  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="product-name">RiskGraph AI</p>
          <h1>Authorization Removal Analysis</h1>
          <p className="subtitle">GET /admin/export · deterministic mentor scenario</p>
        </div>
        <div className="decision-stack">
          <span className="live-status"><span className="live-dot" /> Live result</span>
          <span className={`decision decision-${analysis.verdict.toLowerCase()}`}>{analysis.verdict}</span>
        </div>
      </header>

      <section className="summary-grid" aria-label="Risk summary">
        <Metric label="Risk before" value={risk.risk_before} detail={risk.category_before} tone="neutral" />
        <Metric label="Risk after" value={risk.risk_after} detail={risk.category_after} tone="danger" />
        <Metric label="Risk delta" value={`+${risk.risk_delta}`} detail="Introduced by this change" tone="danger" />
        <Metric label="New attack paths" value={analysis.graph_delta.new_paths.length} detail="Anonymous to sensitive data" tone="danger" />
      </section>

      <section className="graph-section">
        <div className="section-heading">
          <div><p className="section-kicker">BFS reachability comparison</p><h2>Security graph before and after</h2></div>
          <span className="algorithm-badge">NetworkX · shortest path</span>
        </div>
        <div className="graph-grid">
          <GraphPanel title="Before change" status="Protected by ADMIN" graph={analysis.graph_delta.before} highlightedPath={null} />
          <GraphPanel title="After change" status="Anonymous path exposed" graph={analysis.graph_delta.after} highlightedPath={newPath ?? null} />
        </div>
      </section>

      <section className="details-grid">
        <div className="detail-panel">
          <div className="section-heading compact"><div><p className="section-kicker">Transparent formula</p><h2>Risk components</h2></div></div>
          <div className="component-table">
            {Object.entries(risk.components).map(([name, component]) => (
              <div className="component-row" key={name}>
                <span>{formatLabel(name)}</span>
                <span>{component.score} × {component.weight.toFixed(2)}</span>
                <strong>{component.weighted_score}</strong>
              </div>
            ))}
          </div>
        </div>

        <div className="detail-panel evidence-panel">
          <div className="section-heading compact"><div><p className="section-kicker">Deterministic findings</p><h2>Evidence</h2></div></div>
          <ul className="evidence-list">{risk.evidence.map((item) => <li key={item}>{item}</li>)}</ul>
          {newPath && <div className="path-summary"><span>Verified path length</span><strong>{newPath.edges.length} edges</strong></div>}
        </div>
      </section>
    </main>
  );
}

function GraphPanel({ title, status, graph, highlightedPath }: {
  title: string; status: string; graph: SecurityGraph; highlightedPath: PathEvidence | null;
}) {
  const nodes = new Map(graph.nodes.map((node) => [node.id, node]));
  const pathEdges = new Set<string>();
  highlightedPath?.nodes.slice(0, -1).forEach((source, index) => {
    pathEdges.add(`${source}|${highlightedPath.nodes[index + 1]}`);
  });
  return (
    <article className={`graph-panel ${highlightedPath ? "graph-panel-danger" : ""}`}>
      <div className="graph-header">
        <div><h3>{title}</h3><p>{graph.nodes.length} nodes · {graph.edges.length} edges</p></div>
        <span className={highlightedPath ? "graph-status danger-text" : "graph-status safe-text"}>{status}</span>
      </div>
      <div className="edge-list">
        {graph.edges.map((edge) => {
          const highlighted = pathEdges.has(`${edge.source_id}|${edge.target_id}`);
          return (
            <div className={`edge-row ${highlighted ? "edge-row-danger" : ""}`} key={`${edge.source_id}-${edge.target_id}-${edge.relationship}`}>
              <GraphNodeView node={nodes.get(edge.source_id)} />
              <div className="relationship"><span>{edge.relationship.replaceAll("_", " ")}</span><i /></div>
              <GraphNodeView node={nodes.get(edge.target_id)} />
            </div>
          );
        })}
      </div>
    </article>
  );
}

function GraphNodeView({ node }: { node?: GraphNode }) {
  return <div className="graph-node"><span>{node?.node_type ?? "UNKNOWN"}</span><strong>{node?.name ?? "Unknown node"}</strong></div>;
}

function Metric({ label, value, detail, tone }: {
  label: string; value: string | number; detail: string; tone: "neutral" | "danger";
}) {
  return <article className={`metric metric-${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small></article>;
}

function StatusScreen({ title, detail, children }: {
  title: string; detail: string; children?: React.ReactNode;
}) {
  return <main className="status-screen"><p className="product-name">RiskGraph AI</p><h1>{title}</h1><p>{detail}</p>{children}</main>;
}

function formatLabel(value: string) {
  return value.split("_").map((word) => word[0].toUpperCase() + word.slice(1)).join(" ");
}

createRoot(document.getElementById("root")!).render(<React.StrictMode><App /></React.StrictMode>);
