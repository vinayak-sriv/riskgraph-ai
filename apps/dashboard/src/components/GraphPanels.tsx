import { useEffect, useState } from "react";
import type { Viewport } from "@xyflow/react";
import { ChevronRight } from "lucide-react";
import { SecurityGraph } from "./SecurityGraph";
import type {
  AnalysisResult,
  SecurityGraph as SecurityGraphData,
} from "../types";
import { formatLabel, type Theme } from "../view-model";

export function GraphPanel({
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

export function GraphSummary({
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
