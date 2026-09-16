import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  Check,
  ChevronRight,
  Copy,
  Expand,
  Link2,
  Minimize2,
  Network,
  SlidersHorizontal,
} from "lucide-react";
import type { Viewport } from "@xyflow/react";
import { SecurityGraph } from "./SecurityGraph";
import { NodeInspector } from "./NodeInspector";
import { useGraphDiff, type DiffFilter } from "./useGraphDiff";
import { useGraphPresentationMode } from "./useGraphPresentationMode";
import type {
  AnalysisResult,
  SecurityGraph as SecurityGraphData,
} from "../types";
import {
  formatLabel,
  sourceEvidenceForNode,
  type SelectedNode,
  type Theme,
} from "../view-model";

type GraphMode = "compare" | "before" | "after";
type FocusTarget = { nodeId: string; requestId: number };

export function GraphComparison({
  analysis,
  theme,
  onNotify,
  focusTarget,
  onEvidenceFocus,
}: {
  analysis: AnalysisResult;
  theme: Theme;
  onNotify: (message: string) => void;
  focusTarget?: FocusTarget | null;
  onEvidenceFocus?: (evidence: string) => void;
}) {
  const presentation = useGraphPresentationMode(analysis);
  const [graphMode, setGraphMode] = useState<GraphMode>(() =>
    presentation.allowCompare &&
    !window.matchMedia("(max-width: 760px)").matches
      ? "compare"
      : "after",
  );
  const [nodeTypeFilter, setNodeTypeFilter] = useState("ALL");
  const [diffFilter, setDiffFilter] = useState<DiffFilter>(() =>
    presentation.mode === "NORMAL"
      ? "all"
      : analysis.graph_delta.new_paths.length
        ? "path"
        : "changed",
  );
  const [detailedLoaded, setDetailedLoaded] = useState(
    !presentation.requiresDetailedLoad,
  );
  const [selectedNode, setSelectedNode] = useState<SelectedNode | null>(null);
  const [copied, setCopied] = useState(false);
  const [pathIndex, setPathIndex] = useState(0);
  const [fullScreen, setFullScreen] = useState(false);
  const [syncViews, setSyncViews] = useState(true);
  const [sharedViewport, setSharedViewport] = useState<Viewport | undefined>();
  const fullScreenButton = useRef<HTMLButtonElement>(null);
  const graphStage = useRef<HTMLElement>(null);

  useEffect(() => {
    setSelectedNode(null);
    setPathIndex(0);
    setNodeTypeFilter("ALL");
    setGraphMode(
      presentation.allowCompare &&
        !window.matchMedia("(max-width: 760px)").matches
        ? "compare"
        : "after",
    );
    setDiffFilter(
      presentation.mode === "NORMAL"
        ? "all"
        : analysis.graph_delta.new_paths.length
          ? "path"
          : "changed",
    );
    setDetailedLoaded(!presentation.requiresDetailedLoad);
    setCopied(false);
    setSharedViewport(undefined);
  }, [analysis, presentation]);
  useEffect(() => {
    if (!copied) return;
    const timer = window.setTimeout(() => setCopied(false), 1600);
    return () => window.clearTimeout(timer);
  }, [copied]);
  useEffect(() => {
    if (!fullScreen) return;
    const overflow = document.body.style.overflow;
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setFullScreen(false);
        fullScreenButton.current?.focus();
        return;
      }
      if (event.key !== "Tab") return;
      const focusable = Array.from(
        graphStage.current?.querySelectorAll<HTMLElement>(
          'button:not([disabled]), select:not([disabled]), a[href], summary, [tabindex]:not([tabindex="-1"])',
        ) ?? [],
      ).filter((element) => !element.hasAttribute("hidden"));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKey);
    return () => {
      document.body.style.overflow = overflow;
      window.removeEventListener("keydown", handleKey);
    };
  }, [fullScreen]);

  const newPath = analysis.graph_delta.new_paths[pathIndex] ?? null;
  const pathText = newPath?.nodes.join(" -> ") ?? "No new path detected";
  const nodeTypes = useMemo(
    () => [
      "ALL",
      ...Array.from(
        new Set(
          [
            ...analysis.graph_delta.before.nodes,
            ...analysis.graph_delta.after.nodes,
          ].map((node) => node.node_type),
        ),
      ).sort(),
    ],
    [analysis],
  );
  const graphDiff = useGraphDiff(analysis, diffFilter, newPath);
  const { visibleBefore, visibleAfter, changeCounts, afterNodeCounts } =
    graphDiff;

  const selectNode = (
    nodeId: string,
    graph: SecurityGraphData,
    revision: "Before" | "After",
  ) => {
    const node = graph.nodes.find((candidate) => candidate.id === nodeId);
    if (!node) return;
    setSelectedNode({
      node,
      graph,
      revision,
      isPathNode:
        revision === "After" && (newPath?.nodes.includes(nodeId) ?? false),
    });
  };
  useEffect(() => {
    if (!focusTarget) return;
    const afterNode = analysis.graph_delta.after.nodes.find(
      (node) => node.id === focusTarget.nodeId,
    );
    const graph = afterNode
      ? analysis.graph_delta.after
      : analysis.graph_delta.before;
    const revision = afterNode ? "After" : "Before";
    if (graph.nodes.some((node) => node.id === focusTarget.nodeId)) {
      setDetailedLoaded(true);
      setGraphMode(revision === "After" ? "after" : "before");
      setDiffFilter("all");
      selectNode(focusTarget.nodeId, graph, revision);
      document.getElementById("analysis-graph")?.scrollIntoView({
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "instant"
          : "smooth",
      });
    }
  }, [focusTarget]);

  const copyPath = async () => {
    try {
      await navigator.clipboard.writeText(pathText);
      setCopied(true);
      onNotify("Attack path copied");
    } catch {
      onNotify(
        "Clipboard unavailable. You can export the analysis JSON instead.",
      );
    }
  };
  const matchingEvidence = selectedNode
    ? analysis.risk_result.evidence.filter(
        (item) =>
          item.toLowerCase().includes(selectedNode.node.name.toLowerCase()) ||
          (selectedNode.isPathNode && /newly reach|new path/i.test(item)),
      )
    : [];
  const sourceMatches = selectedNode
    ? sourceEvidenceForNode(analysis, selectedNode.node)
    : [];

  return (
    <section
      ref={graphStage}
      id="analysis-graph"
      className={`section-block graph-stage${fullScreen ? " is-fullscreen" : ""}`}
      aria-labelledby="graph-title"
      role={fullScreen ? "dialog" : undefined}
      aria-modal={fullScreen || undefined}
    >
      <div className="section-title-row graph-title-row">
        <div className="section-label">
          <span className="section-number">02</span>
          <div>
            <h2 id="graph-title">Security graph comparison</h2>
            <p className="section-description">
              Trace access from identity to sensitive data.
            </p>
          </div>
        </div>
        <div className="graph-toolbar" aria-label="Graph controls">
          {analysis.graph_delta.new_paths.length > 1 && (
            <label className="filter-control">
              <span>Path</span>
              <select
                aria-label="Attack path"
                value={pathIndex}
                onChange={(event) => {
                  setPathIndex(Number(event.target.value));
                  setSelectedNode(null);
                }}
              >
                {analysis.graph_delta.new_paths.map((path, index) => (
                  <option key={index} value={index}>
                    {index + 1}.{" "}
                    {analysis.graph_delta.after.nodes.find(
                      (node) => node.id === path.target,
                    )?.name ?? path.target}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label className="filter-control">
            <span>Show</span>
            <select
              aria-label="Graph changes"
              value={diffFilter}
              onChange={(event) =>
                setDiffFilter(event.target.value as DiffFilter)
              }
            >
              <option value="all">All graph data</option>
              <option value="changed">Changed only</option>
              <option value="path" disabled={!newPath}>
                Selected path
              </option>
            </select>
          </label>
          <label className="filter-control">
            <span>Highlight</span>
            <select
              aria-label="Highlight"
              value={nodeTypeFilter}
              onChange={(event) => setNodeTypeFilter(event.target.value)}
            >
              {nodeTypes.map((type) => (
                <option value={type} key={type}>
                  {type === "ALL" ? "All node types" : formatLabel(type)}
                </option>
              ))}
            </select>
          </label>
          <button
            className={`icon-button ${syncViews ? "active" : ""}`}
            type="button"
            aria-pressed={syncViews}
            aria-label="Synchronize graph views"
            title="Synchronize graph views"
            onClick={() => setSyncViews((value) => !value)}
          >
            <Link2 size={16} />
          </button>
          <button
            ref={fullScreenButton}
            className="icon-button"
            type="button"
            aria-label={
              fullScreen ? "Exit full-screen graph" : "Open full-screen graph"
            }
            title={
              fullScreen ? "Exit full-screen graph" : "Open full-screen graph"
            }
            onClick={() => setFullScreen((value) => !value)}
          >
            {fullScreen ? <Minimize2 size={16} /> : <Expand size={16} />}
          </button>
          <div className="segmented-control" aria-label="Graph display mode">
            {(["compare", "before", "after"] as GraphMode[]).map((mode) => (
              <button
                type="button"
                key={mode}
                className={graphMode === mode ? "active" : ""}
                aria-pressed={graphMode === mode}
                disabled={mode === "compare" && !presentation.allowCompare}
                onClick={() => setGraphMode(mode)}
              >
                {formatLabel(mode)}
              </button>
            ))}
          </div>
        </div>
      </div>

      <details className="graph-legend" open>
        <summary>
          <SlidersHorizontal size={14} /> Graph legend
        </summary>
        <div>
          <span>
            <i className="legend-user" />
            Identity
          </span>
          <span>
            <i className="legend-endpoint" />
            Endpoint
          </span>
          <span>
            <i className="legend-system" />
            Application
          </span>
          <span>
            <i className="legend-data" />
            Data
          </span>
          <span>
            <i className="legend-added" />
            Added
          </span>
          <span>
            <i className="legend-removed" />
            Removed
          </span>
          <span>
            <i className="legend-path" />
            New attack path
          </span>
        </div>
      </details>
      {presentation.mode !== "NORMAL" && (
        <div className="graph-size-notice" role="status">
          <strong>{formatLabel(presentation.mode)} graph mode</strong>
          <span>
            Largest revision: {presentation.largestRevisionNodes} nodes. Full
            deterministic counts remain available below.
          </span>
        </div>
      )}
      <div className="graph-change-summary" aria-label="Graph changes">
        {changeCounts.addedEdges.map(({ type, count }) => (
          <span className="change-added" key={`added-edge-${type}`}>
            + {count} {formatLabel(type)} {count === 1 ? "edge" : "edges"}
          </span>
        ))}
        {changeCounts.removedEdges.map(({ type, count }) => (
          <span className="change-removed" key={`removed-edge-${type}`}>
            − {count} {formatLabel(type)} {count === 1 ? "edge" : "edges"}
          </span>
        ))}
        {changeCounts.addedNodes.map(({ type, count }) => (
          <span className="change-added" key={`added-node-${type}`}>
            + {count} {formatLabel(type)} {count === 1 ? "node" : "nodes"}
          </span>
        ))}
        {changeCounts.removedNodes.map(({ type, count }) => (
          <span className="change-removed" key={`removed-node-${type}`}>
            − {count} {formatLabel(type)} {count === 1 ? "node" : "nodes"}
          </span>
        ))}
      </div>

      <div className="mobile-findings surface">
        <div>
          <span>Deterministic findings</span>
          <strong>{analysis.risk_result.evidence.length}</strong>
        </div>
        <ol>
          {analysis.risk_result.evidence.slice(0, 2).map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ol>
        <a href="#analysis-evidence">
          Review all evidence <ChevronRight size={14} />
        </a>
      </div>

      {newPath && (
        <div className="path-strip">
          <div className="path-strip-heading">
            <span>
              <AlertTriangle size={16} /> Newly reachable path
            </span>
            <button
              className="icon-button subtle"
              title="Copy attack path"
              aria-label="Copy attack path"
              onClick={() => void copyPath()}
            >
              {copied ? <Check size={16} /> : <Copy size={16} />}
            </button>
          </div>
          <div className="path-nodes">
            {newPath.nodes.map((node, index) => (
              <span key={node}>
                <button
                  type="button"
                  title={node}
                  onClick={() =>
                    selectNode(node, analysis.graph_delta.after, "After")
                  }
                >
                  {analysis.graph_delta.after.nodes.find(
                    (item) => item.id === node,
                  )?.name ?? node}
                </button>
                {index < newPath.nodes.length - 1 && <ChevronRight size={15} />}
              </span>
            ))}
          </div>
        </div>
      )}

      {!detailedLoaded && (
        <GraphSummary
          analysis={analysis}
          afterNodeCounts={afterNodeCounts}
          onLoad={() => setDetailedLoaded(true)}
        />
      )}

      {detailedLoaded && (
        <div className={`graph-workspace graph-mode-${graphMode}`}>
          {graphMode !== "after" && (
            <GraphPanel
              title="Before change"
              subtitle="Previous revision"
              graph={visibleBefore}
              fullGraph={analysis.graph_delta.before}
              status="Before"
              nodeTypeFilter={nodeTypeFilter}
              selectedNodeId={
                selectedNode?.revision === "Before"
                  ? selectedNode.node.id
                  : null
              }
              onNodeSelect={(nodeId) =>
                selectNode(nodeId, analysis.graph_delta.before, "Before")
              }
              theme={theme}
              viewport={syncViews ? sharedViewport : undefined}
              onViewportChange={syncViews ? setSharedViewport : undefined}
            />
          )}
          {graphMode !== "before" && (
            <GraphPanel
              title="After change"
              subtitle={
                newPath
                  ? "New anonymous access detected"
                  : "No new anonymous sensitive path"
              }
              graph={visibleAfter}
              fullGraph={analysis.graph_delta.after}
              status={newPath ? "New path" : "No new path"}
              danger={Boolean(newPath)}
              highlightedPath={newPath}
              nodeTypeFilter={nodeTypeFilter}
              selectedNodeId={
                selectedNode?.revision === "After" ? selectedNode.node.id : null
              }
              onNodeSelect={(nodeId) =>
                selectNode(nodeId, analysis.graph_delta.after, "After")
              }
              theme={theme}
              viewport={syncViews ? sharedViewport : undefined}
              onViewportChange={syncViews ? setSharedViewport : undefined}
            />
          )}
        </div>
      )}
      <div className="graph-caption">
        <span>
          <Network size={14} /> NetworkX · BFS shortest path
        </span>
        <span>Scroll to zoom · Drag to pan · Select a node to inspect</span>
      </div>
      {selectedNode && (
        <NodeInspector
          selection={selectedNode}
          evidence={matchingEvidence}
          sourceEvidence={sourceMatches}
          onEvidenceFocus={onEvidenceFocus}
          onClose={() => setSelectedNode(null)}
        />
      )}
    </section>
  );
}

function GraphPanel({
  title,
  subtitle,
  graph,
  fullGraph,
  status,
  danger = false,
  highlightedPath = null,
  nodeTypeFilter,
  selectedNodeId,
  onNodeSelect,
  theme,
  viewport,
  onViewportChange,
}: {
  title: string;
  subtitle: string;
  graph: SecurityGraphData;
  fullGraph: SecurityGraphData;
  status: string;
  danger?: boolean;
  highlightedPath?: AnalysisResult["graph_delta"]["new_paths"][number] | null;
  nodeTypeFilter: string;
  selectedNodeId: string | null;
  onNodeSelect: (nodeId: string) => void;
  theme: Theme;
  viewport?: Viewport;
  onViewportChange?: (viewport: Viewport) => void;
}) {
  const pageSize = 100;
  const [directoryPage, setDirectoryPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(fullGraph.nodes.length / pageSize));
  useEffect(() => setDirectoryPage(0), [fullGraph]);
  const directoryNodes = fullGraph.nodes.slice(
    directoryPage * pageSize,
    (directoryPage + 1) * pageSize,
  );
  return (
    <article className={`graph-panel ${danger ? "danger" : ""}`}>
      <header>
        <div>
          <h3>{title}</h3>
          <p>{subtitle}</p>
        </div>
        <div className="graph-meta">
          <span>
            {graph.nodes.length} of {fullGraph.nodes.length} nodes ·{" "}
            {graph.edges.length} edges
          </span>
          <strong>{status}</strong>
        </div>
      </header>
      <SecurityGraph
        graph={graph}
        highlightedPath={highlightedPath}
        label={`${title}: ${subtitle}`}
        nodeTypeFilter={nodeTypeFilter}
        selectedNodeId={selectedNodeId}
        onNodeSelect={onNodeSelect}
        theme={theme}
        viewport={viewport}
        onViewportChange={onViewportChange}
      />
      <details className="node-directory">
        <summary>
          Inspect all {fullGraph.nodes.length} nodes{" "}
          <span>Paginated text view</span>
        </summary>
        <ul>
          {directoryNodes.map((node) => (
            <li key={node.id}>
              <button type="button" onClick={() => onNodeSelect(node.id)}>
                <span>{formatLabel(node.node_type)}</span>
                <strong>{node.name}</strong>
                <ChevronRight size={15} />
              </button>
            </li>
          ))}
        </ul>
        <nav
          className="node-directory-pagination"
          aria-label={`${title} node pages`}
        >
          <button
            type="button"
            disabled={directoryPage === 0}
            onClick={() => setDirectoryPage((page) => page - 1)}
          >
            Previous
          </button>
          <span aria-live="polite">
            Page {directoryPage + 1} of {pageCount}
          </span>
          <button
            type="button"
            disabled={directoryPage + 1 >= pageCount}
            onClick={() => setDirectoryPage((page) => page + 1)}
          >
            Next
          </button>
        </nav>
      </details>
    </article>
  );
}

function GraphSummary({
  analysis,
  afterNodeCounts,
  onLoad,
}: {
  analysis: AnalysisResult;
  afterNodeCounts: { type: string; count: number }[];
  onLoad: () => void;
}) {
  return (
    <section
      className="graph-cluster-summary"
      aria-labelledby="cluster-summary-title"
    >
      <div>
        <h3 id="cluster-summary-title">After-change graph summary</h3>
        <p>
          {analysis.graph_delta.after.nodes.length} nodes and{" "}
          {analysis.graph_delta.after.edges.length} edges. Interactive layout is
          deferred to protect browser responsiveness.
        </p>
      </div>
      <ul aria-label="Node clusters by type">
        {afterNodeCounts.map(({ type, count }) => (
          <li key={type}>
            <span>{formatLabel(type)}</span>
            <strong>{count}</strong>
          </li>
        ))}
      </ul>
      <button className="secondary-button" type="button" onClick={onLoad}>
        Load focused graph detail
      </button>
    </section>
  );
}
