import { useCallback, useEffect, useState, type ReactNode } from "react";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  ChevronRight,
  CircleDot,
  Copy,
  Database,
  Download,
  FileSearch,
  GitCompareArrows,
  LayoutDashboard,
  Moon,
  Network,
  RefreshCw,
  Route,
  ShieldAlert,
  ShieldCheck,
  Sun,
  WifiOff,
  X
} from "lucide-react";
import { SecurityGraph } from "./components/SecurityGraph";
import type { AnalysisResult, Category, GraphNode, SecurityGraph as SecurityGraphData, Verdict } from "./types";

const platformUrl = import.meta.env.VITE_PLATFORM_API_BASE_URL ?? "http://localhost:8080";
type GraphMode = "compare" | "before" | "after";
type SectionId = "overview" | "graph" | "evidence";
type Theme = "light" | "dark";
type SelectedNode = { node: GraphNode; graph: SecurityGraphData; revision: "Before" | "After"; isPathNode: boolean };

export default function App() {
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [graphMode, setGraphMode] = useState<GraphMode>(() => window.matchMedia("(max-width: 760px)").matches ? "after" : "compare");
  const [copied, setCopied] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [activeSection, setActiveSection] = useState<SectionId>("overview");
  const [nodeTypeFilter, setNodeTypeFilter] = useState("ALL");
  const [selectedNode, setSelectedNode] = useState<SelectedNode | null>(null);
  const [theme, setTheme] = useState<Theme>(() => document.documentElement.dataset.theme === "dark" ? "dark" : "light");

  const loadAnalysis = useCallback(async (notify = false) => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${platformUrl}/demo/authorization-removal`);
      if (!response.ok) throw new Error(`Platform API returned HTTP ${response.status}`);
      setAnalysis((await response.json()) as AnalysisResult);
      if (notify) setToast("Analysis refreshed");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to load analysis");
      if (notify) setToast("Refresh failed · showing the last successful result");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadAnalysis(false); }, [loadAnalysis]);
  useEffect(() => {
    if (!toast) return;
    const timeout = window.setTimeout(() => setToast(null), 2200);
    return () => window.clearTimeout(timeout);
  }, [toast]);
  useEffect(() => {
    if (!analysis) return;
    const sections = (["overview", "graph", "evidence"] as SectionId[])
      .map((id) => document.getElementById(id))
      .filter((section): section is HTMLElement => section !== null);
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((entry) => entry.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (visible) setActiveSection(visible.target.id as SectionId);
      },
      { rootMargin: "-15% 0px -65% 0px", threshold: [0, 0.1, 0.3] }
    );
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [analysis]);
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === "Escape") setSelectedNode(null); };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("riskgraph-theme", theme);
  }, [theme]);

  if (loading && !analysis) return <StatusScreen loading title="Running deterministic analysis" detail="Building security graphs and comparing reachability." />;
  if (!analysis) {
    return (
      <StatusScreen title="Analysis unavailable" detail={error ?? "No result was returned."}>
        <button className="primary-button" onClick={() => void loadAnalysis(true)}><RefreshCw size={16} /> Retry analysis</button>
      </StatusScreen>
    );
  }

  const risk = analysis.risk_result;
  const newPath = analysis.graph_delta.new_paths[0] ?? null;
  const pathText = newPath?.nodes.join(" -> ") ?? "No new path detected";
  const nodeTypes = ["ALL", ...Array.from(new Set([...analysis.graph_delta.before.nodes, ...analysis.graph_delta.after.nodes].map((node) => node.node_type))).sort()];
  const beforeEdgeKeys = new Set(analysis.graph_delta.before.edges.map((edge) => `${edge.source_id}|${edge.target_id}|${edge.relationship}`));
  const afterEdgeKeys = new Set(analysis.graph_delta.after.edges.map((edge) => `${edge.source_id}|${edge.target_id}|${edge.relationship}`));
  const addedEdges = analysis.graph_delta.after.edges.filter((edge) => !beforeEdgeKeys.has(`${edge.source_id}|${edge.target_id}|${edge.relationship}`));
  const removedEdges = analysis.graph_delta.before.edges.filter((edge) => !afterEdgeKeys.has(`${edge.source_id}|${edge.target_id}|${edge.relationship}`));
  const afterNodeIds = new Set(analysis.graph_delta.after.nodes.map((node) => node.id));
  const removedNodes = analysis.graph_delta.before.nodes.filter((node) => !afterNodeIds.has(node.id));

  const selectNode = (nodeId: string, graph: SecurityGraphData, revision: "Before" | "After") => {
    const node = graph.nodes.find((candidate) => candidate.id === nodeId);
    if (!node) return;
    setSelectedNode({ node, graph, revision, isPathNode: newPath?.nodes.includes(nodeId) ?? false });
  };
  const toggleTheme = () => setTheme((current) => current === "light" ? "dark" : "light");

  const downloadResult = () => {
    const blob = new Blob([JSON.stringify(analysis, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${analysis.scenario || "riskgraph-analysis"}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
    setToast("Analysis JSON exported");
  };

  const copyPath = async () => {
    await navigator.clipboard.writeText(pathText);
    setCopied(true);
    setToast("Attack path copied");
    window.setTimeout(() => setCopied(false), 1600);
  };

  return (
    <div className="app-shell">
      <Sidebar activeSection={activeSection} />
      <main className={`workspace${loading ? " is-refreshing" : ""}`}>
        <header className="workspace-header">
          <div>
            <div className="breadcrumb"><span>Projects</span><ChevronRight size={14} /><strong>Mentor Demo</strong></div>
            <h1>Authorization removal</h1>
            <p>Security impact analysis for <code>GET /admin/export</code></p>
          </div>
          <div className="header-actions">
            <ConnectionStatus loading={loading} error={error} />
            <button className="icon-button" title={`Use ${theme === "light" ? "dark" : "light"} mode`} aria-label={`Use ${theme === "light" ? "dark" : "light"} mode`} onClick={toggleTheme}>{theme === "light" ? <Moon size={17} /> : <Sun size={17} />}</button>
            <button className="icon-button" title="Refresh analysis" aria-label="Refresh analysis" disabled={loading} onClick={() => void loadAnalysis(true)}><RefreshCw className={loading ? "spin" : ""} size={17} /></button>
            <button className="secondary-button" onClick={downloadResult}><Download size={16} /> Export JSON</button>
          </div>
        </header>

        <div className="fixture-notice"><FileSearch size={16} /><span><strong>Fixture-backed analysis</strong> · deterministic graph and risk engines are live; source extraction and validation are pending.</span></div>

        <section id="overview" className="section-block" aria-labelledby="overview-title">
          <div className="section-title-row">
            <div><p className="eyebrow">Analysis overview</p><h2 id="overview-title">Decision summary</h2></div>
            <VerdictBadge verdict={analysis.verdict} />
          </div>

          <div className="metric-grid">
            <Metric icon={<ShieldCheck />} label="Risk before" value={risk.risk_before} detail={risk.category_before} tone="safe" />
            <Metric icon={<ShieldAlert />} label="Risk after" value={risk.risk_after} detail={risk.category_after} tone="danger" />
            <Metric icon={<GitCompareArrows />} label="Risk delta" value={`+${risk.risk_delta}`} detail="Introduced by change" tone="warning" />
            <Metric icon={<Route />} label="New attack paths" value={analysis.graph_delta.new_paths.length} detail="Anonymous to sensitive" tone="danger" />
          </div>

          <div className="overview-grid">
            <div className="risk-comparison surface">
              <div className="surface-heading"><div><p className="eyebrow">Impact</p><h3>Risk comparison</h3></div><span className="delta-label">+{risk.risk_delta} points</span></div>
              <RiskBar label="Before" score={risk.risk_before} category={risk.category_before} />
              <RiskBar label="After" score={risk.risk_after} category={risk.category_after} danger />
              <div className="scale-labels"><span>0 Low</span><span>50 Medium</span><span>100 Critical</span></div>
            </div>
            <div className="pipeline surface">
              <div className="surface-heading"><div><p className="eyebrow">Execution</p><h3>Pipeline status</h3></div><span className="runtime-badge">Deterministic</span></div>
              <PipelineStep icon={<CheckCircle2 />} label="Graph construction" detail="Before and after" state="complete" />
              <PipelineStep icon={<CheckCircle2 />} label="BFS reachability" detail={`${analysis.graph_delta.new_paths.length} new ${analysis.graph_delta.new_paths.length === 1 ? "path" : "paths"}`} state="complete" />
              <PipelineStep icon={<CheckCircle2 />} label="Risk and verdict" detail={`${risk.risk_after}/100 · ${analysis.verdict}`} state="complete" />
              <PipelineStep icon={<CircleDot />} label="AI and validation" detail="Roadmap Weeks 9–10" state="pending" />
            </div>
          </div>
        </section>

        <section id="graph" className="section-block" aria-labelledby="graph-title">
          <div className="section-title-row graph-title-row">
            <div><p className="eyebrow">NetworkX · BFS shortest path</p><h2 id="graph-title">Security graph comparison</h2></div>
            <div className="graph-toolbar">
              <label className="filter-control"><span>Highlight</span><select value={nodeTypeFilter} onChange={(event) => setNodeTypeFilter(event.target.value)}>{nodeTypes.map((type) => <option value={type} key={type}>{type === "ALL" ? "All node types" : formatLabel(type)}</option>)}</select></label>
              <div className="segmented-control" aria-label="Graph display mode">
                {(["compare", "before", "after"] as GraphMode[]).map((mode) => (
                  <button key={mode} className={graphMode === mode ? "active" : ""} aria-pressed={graphMode === mode} onClick={() => setGraphMode(mode)}>{formatLabel(mode)}</button>
                ))}
              </div>
            </div>
          </div>

          <div className="graph-legend" aria-label="Graph node legend">
            <span><i className="legend-user" />Identity</span><span><i className="legend-endpoint" />Endpoint</span><span><i className="legend-system" />Application</span><span><i className="legend-data" />Data</span><span><i className="legend-path" />New attack path</span>
          </div>
          <div className="graph-change-summary" aria-label="Graph changes">
            {addedEdges.map((edge) => <span className="change-added" key={`added-${edge.source_id}-${edge.target_id}`}>+ {formatLabel(edge.relationship)} edge</span>)}
            {removedEdges.map((edge) => <span className="change-removed" key={`removed-${edge.source_id}-${edge.target_id}`}>− {formatLabel(edge.relationship)} edge</span>)}
            {removedNodes.map((node) => <span className="change-removed" key={`removed-${node.id}`}>− {formatLabel(node.node_type)} node</span>)}
          </div>

          <div className={`graph-workspace graph-mode-${graphMode}`}>
            {graphMode !== "after" && <GraphPanel title="Before change" subtitle="ADMIN authorization required" graph={analysis.graph_delta.before} status="Protected" nodeTypeFilter={nodeTypeFilter} selectedNodeId={selectedNode?.revision === "Before" ? selectedNode.node.id : null} onNodeSelect={(nodeId) => selectNode(nodeId, analysis.graph_delta.before, "Before")} theme={theme} />}
            {graphMode !== "before" && <GraphPanel title="After change" subtitle="Anonymous route reaches customer data" graph={analysis.graph_delta.after} status="Exposed" danger highlightedPath={newPath} nodeTypeFilter={nodeTypeFilter} selectedNodeId={selectedNode?.revision === "After" ? selectedNode.node.id : null} onNodeSelect={(nodeId) => selectNode(nodeId, analysis.graph_delta.after, "After")} theme={theme} />}
          </div>

          {newPath && <div className="path-strip">
            <div className="path-strip-heading"><span><AlertTriangle size={16} /> Newly reachable path</span><button className="icon-button subtle" title="Copy attack path" aria-label="Copy attack path" onClick={() => void copyPath()}>{copied ? <Check size={16} /> : <Copy size={16} />}</button></div>
            <div className="path-nodes">
              {newPath?.nodes.map((node, index) => <span key={node}><code>{node}</code>{index < newPath.nodes.length - 1 && <ChevronRight size={15} />}</span>)}
            </div>
          </div>}
        </section>

        <section id="evidence" className="section-block evidence-section" aria-labelledby="evidence-title">
          <div className="section-title-row"><div><p className="eyebrow">Auditable inputs</p><h2 id="evidence-title">Evidence and scoring</h2></div></div>
          <div className="evidence-grid">
            <div className="surface evidence-surface">
              <div className="surface-heading"><div><p className="eyebrow">Deterministic findings</p><h3>Evidence</h3></div><span className="count-badge">{risk.evidence.length}</span></div>
              <ol className="evidence-list">
                {risk.evidence.map((item, index) => <li key={item}><span>{index + 1}</span><p>{item}</p></li>)}
              </ol>
            </div>
            <div className="surface factors-surface">
              <div className="surface-heading"><div><p className="eyebrow">Transparent formula</p><h3>Risk components</h3></div><span className="formula-total">Total {risk.risk_after}</span></div>
              <div className="factor-list">
                {Object.entries(risk.components).map(([name, component]) => (
                  <div className="factor-row" key={name}>
                    <div><span>{formatLabel(name)}</span><small>{component.score} × {component.weight.toFixed(2)}</small></div>
                    <div className="factor-track"><i style={{ width: `${component.score}%` }} /></div>
                    <strong>{component.weighted_score}</strong>
                  </div>
                ))}
              </div>
            </div>
            <div className="surface policy-surface">
              <div className="policy-icon"><ShieldAlert size={20} /></div>
              <div><p className="eyebrow">Deterministic decision</p><h3>Why this change is blocked</h3><p>A new anonymous path reaches a HIGH-sensitivity resource after an ADMIN authorization requirement was removed. The resulting score of {risk.risk_after} is {risk.category_after}.</p></div>
              <VerdictBadge verdict={analysis.verdict} />
            </div>
          </div>
        </section>
      </main>
      {selectedNode && <NodeInspector selection={selectedNode} onClose={() => setSelectedNode(null)} />}
      {toast && <div className="toast" role="status"><CheckCircle2 size={16} />{toast}</div>}
    </div>
  );
}

function Sidebar({ activeSection }: { activeSection: SectionId }) {
  const items = [
    { href: "#overview", label: "Overview", icon: <LayoutDashboard size={18} /> },
    { href: "#graph", label: "Graph diff", icon: <Network size={18} /> },
    { href: "#evidence", label: "Evidence", icon: <FileSearch size={18} /> }
  ];
  return (
    <aside className="sidebar">
      <div className="brand"><span><ShieldCheck size={20} /></span><div><strong>RiskGraph</strong><small>Security analysis</small></div></div>
      <nav aria-label="Analysis navigation">{items.map((item) => <a href={item.href} className={activeSection === item.href.slice(1) ? "active" : ""} aria-current={activeSection === item.href.slice(1) ? "location" : undefined} key={item.href}>{item.icon}<span>{item.label}</span></a>)}</nav>
      <div className="sidebar-footer"><Database size={16} /><div><strong>Mentor Demo</strong><small>Local environment</small></div></div>
    </aside>
  );
}

function Metric({ icon, label, value, detail, tone }: { icon: ReactNode; label: string; value: string | number; detail: string; tone: "safe" | "danger" | "warning" }) {
  return <article className={`metric metric-${tone}`}><div className="metric-top"><span>{label}</span><i>{icon}</i></div><strong>{value}</strong><small>{detail}</small></article>;
}

function RiskBar({ label, score, category, danger = false }: { label: string; score: number; category: Category; danger?: boolean }) {
  return <div className="risk-bar-row"><div><span>{label}</span><strong>{score}</strong></div><div className="risk-track"><i className={danger ? "danger" : "safe"} style={{ width: `${score}%` }} /></div><small>{category}</small></div>;
}

function PipelineStep({ icon, label, detail, state }: { icon: ReactNode; label: string; detail: string; state: "complete" | "pending" }) {
  return <div className={`pipeline-step ${state}`}><span>{icon}</span><div><strong>{label}</strong><small>{detail}</small></div></div>;
}

function GraphPanel({ title, subtitle, graph, status, danger = false, highlightedPath = null, nodeTypeFilter, selectedNodeId, onNodeSelect, theme }: {
  title: string;
  subtitle: string;
  graph: SecurityGraphData;
  status: string;
  danger?: boolean;
  highlightedPath?: AnalysisResult["graph_delta"]["new_paths"][number] | null;
  nodeTypeFilter: string;
  selectedNodeId: string | null;
  onNodeSelect: (nodeId: string) => void;
  theme: Theme;
}) {
  return (
    <article className={`graph-panel ${danger ? "danger" : ""}`}>
      <header><div><h3>{title}</h3><p>{subtitle}</p></div><div className="graph-meta"><span>{graph.nodes.length} nodes · {graph.edges.length} edges</span><strong>{status}</strong></div></header>
      <SecurityGraph graph={graph} highlightedPath={highlightedPath} label={`${title}: ${subtitle}`} nodeTypeFilter={nodeTypeFilter} selectedNodeId={selectedNodeId} onNodeSelect={onNodeSelect} theme={theme} />
    </article>
  );
}

function ConnectionStatus({ loading, error }: { loading: boolean; error: string | null }) {
  if (loading) return <span className="connection-status checking"><RefreshCw className="spin" size={14} /> Checking platform</span>;
  if (error) return <span className="connection-status unavailable" title={error}><WifiOff size={14} /> Platform unavailable</span>;
  return <span className="connection-status"><CircleDot size={14} /> Platform connected</span>;
}

function NodeInspector({ selection, onClose }: { selection: SelectedNode; onClose: () => void }) {
  const incoming = selection.graph.edges.filter((edge) => edge.target_id === selection.node.id);
  const outgoing = selection.graph.edges.filter((edge) => edge.source_id === selection.node.id);
  const nodeName = (id: string) => selection.graph.nodes.find((node) => node.id === id)?.name ?? id;

  return (
    <>
      <button className="drawer-backdrop" aria-label="Close node inspector" onClick={onClose} />
      <aside className="node-drawer" role="dialog" aria-modal="true" aria-labelledby="node-inspector-title">
        <header><div><p className="eyebrow">{selection.revision} graph · {formatLabel(selection.node.node_type)}</p><h2 id="node-inspector-title">{selection.node.name}</h2></div><button autoFocus className="icon-button" title="Close inspector" aria-label="Close inspector" onClick={onClose}><X size={17} /></button></header>
        <div className="drawer-status"><span className={selection.isPathNode ? "on-path" : "off-path"}>{selection.isPathNode ? "On new attack path" : "Outside new attack path"}</span><code>{selection.node.id}</code></div>
        <section><p className="eyebrow">Incoming connections</p>{incoming.length === 0 ? <p className="empty-copy">No incoming edges</p> : incoming.map((edge) => <ConnectionRow key={`${edge.source_id}-${edge.relationship}`} name={nodeName(edge.source_id)} relationship={edge.relationship} direction="incoming" />)}</section>
        <section><p className="eyebrow">Outgoing connections</p>{outgoing.length === 0 ? <p className="empty-copy">No outgoing edges</p> : outgoing.map((edge) => <ConnectionRow key={`${edge.target_id}-${edge.relationship}`} name={nodeName(edge.target_id)} relationship={edge.relationship} direction="outgoing" />)}</section>
        <section className="provenance-pending"><p className="eyebrow">Source provenance</p><p>Pending source extraction · planned for Week 5</p></section>
      </aside>
    </>
  );
}

function ConnectionRow({ name, relationship, direction }: { name: string; relationship: string; direction: "incoming" | "outgoing" }) {
  return <div className="connection-row"><span>{direction === "incoming" ? "From" : "To"}</span><strong>{name}</strong><small>{formatLabel(relationship)}</small></div>;
}

function VerdictBadge({ verdict }: { verdict: Verdict }) {
  const Icon = verdict === "ALLOW" ? CheckCircle2 : verdict === "BLOCK" ? ShieldAlert : AlertTriangle;
  return <span className={`verdict verdict-${verdict.toLowerCase()}`}><Icon size={17} /> {verdict}</span>;
}

function StatusScreen({ title, detail, loading = false, children }: { title: string; detail: string; loading?: boolean; children?: ReactNode }) {
  return <main className="status-screen"><div className="status-mark"><ShieldCheck size={28} />{loading && <span className="spinner" />}</div><p className="eyebrow">RiskGraph AI</p><h1>{title}</h1><p>{detail}</p>{children}</main>;
}

function formatLabel(value: string) {
  return value.split("_").map((word) => word[0].toUpperCase() + word.slice(1)).join(" ");
}
