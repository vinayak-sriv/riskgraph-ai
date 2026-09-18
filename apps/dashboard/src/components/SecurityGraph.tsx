import { useEffect, useMemo, useRef, useState } from "react";
import {
  Box,
  Database,
  KeyRound,
  Route,
  Server,
  UserRound,
} from "lucide-react";
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  useNodesState,
  type Edge,
  type Node,
  type NodeProps,
  type Viewport,
} from "@xyflow/react";
import type {
  PathEvidence,
  SecurityGraph as SecurityGraphData,
} from "../types";
import { requestGraphLayout } from "./graph-layout";

const NODE_WIDTH = 240;
const NODE_HEIGHT = 64;

type GraphNodeData = Record<string, unknown> & {
  name: string;
  nodeType: string;
  onInspect?: () => void;
};
type SecurityFlowNode = Node<GraphNodeData, "securityNode">;

const nodeTypes = { securityNode: SecurityNode };

export function reconcileNodePositions<
  T extends { id: string; position: { x: number; y: number } },
>(current: T[], next: T[], applyLayout: boolean): T[] {
  const currentById = new Map(current.map((node) => [node.id, node]));
  return next.map((node) => ({
    ...node,
    position: applyLayout
      ? node.position
      : (currentById.get(node.id)?.position ?? node.position),
  }));
}

function SecurityNode({ data }: NodeProps<SecurityFlowNode>) {
  const Icon =
    data.nodeType === "USER"
      ? UserRound
      : data.nodeType === "ROLE"
        ? KeyRound
        : data.nodeType === "ENDPOINT"
          ? Route
          : data.nodeType === "DATABASE" || data.nodeType === "DATA_RESOURCE"
            ? Database
            : data.nodeType === "EXTERNAL_SERVICE"
              ? Server
              : Box;
  return (
    <button
      type="button"
      className="security-node-content"
      onClick={data.onInspect}
      aria-label={`Inspect ${data.nodeType.replaceAll("_", " ")}: ${data.name}`}
      title={data.name}
    >
      <Handle type="target" position={Position.Top} />
      <span className="security-node-icon">
        <Icon size={15} />
      </span>
      <span className="security-node-label">
        <small>{data.nodeType.replaceAll("_", " ")}</small>
        <strong>{data.name}</strong>
      </span>
      <Handle type="source" position={Position.Bottom} />
    </button>
  );
}

function graphElements(
  graph: SecurityGraphData,
  positions: Map<string, { x: number; y: number }>,
  highlightedPath: PathEvidence | null,
  nodeTypeFilter: string,
  selectedNodeId: string | null,
  theme: "light" | "dark",
) {
  const pathPairs = new Set<string>();
  const pathNodes = new Set(highlightedPath?.nodes ?? []);
  const nodeById = new Map(graph.nodes.map((node) => [node.id, node]));
  highlightedPath?.nodes.slice(0, -1).forEach((source, index) => {
    pathPairs.add(`${source}|${highlightedPath.nodes[index + 1]}`);
  });

  const nodes: SecurityFlowNode[] = graph.nodes.map((node) => {
    const isPathNode = pathNodes.has(node.id);
    const isMuted =
      nodeTypeFilter !== "ALL" && node.node_type !== nodeTypeFilter;
    return {
      id: node.id,
      position: positions.get(node.id) ?? { x: 0, y: 0 },
      type: "securityNode",
      data: { name: node.name, nodeType: node.node_type },
      className: `flow-node flow-node-${node.node_type.toLowerCase()}${isPathNode ? " flow-node-path" : ""}${isMuted ? " flow-node-muted" : nodeTypeFilter !== "ALL" ? " flow-node-filter-match" : ""}${selectedNodeId === node.id ? " flow-node-selected" : ""}`,
      ariaLabel: `${node.node_type.replaceAll("_", " ")}: ${node.name}`,
      draggable: true,
      selectable: true,
      style: { width: NODE_WIDTH, height: NODE_HEIGHT },
    };
  });

  const edges: Edge[] = graph.edges.map((edge, index) => {
    const isPathEdge = pathPairs.has(`${edge.source_id}|${edge.target_id}`);
    const source = nodeById.get(edge.source_id);
    const target = nodeById.get(edge.target_id);
    const isMuted =
      nodeTypeFilter !== "ALL" &&
      source?.node_type !== nodeTypeFilter &&
      target?.node_type !== nodeTypeFilter;
    const baseColor = theme === "dark" ? "#b4aacb" : "#675c81";
    const mutedColor = theme === "dark" ? "#514a68" : "#b7afc9";
    const pathColor = theme === "dark" ? "#ff8291" : "#ba304b";
    return {
      id: `${edge.source_id}-${edge.target_id}-${index}`,
      source: edge.source_id,
      target: edge.target_id,
      type: "smoothstep",
      pathOptions: { borderRadius: 10, offset: 18 },
      label: edge.relationship.replaceAll("_", " "),
      animated: false,
      className: `${isPathEdge ? "flow-edge-path" : ""}${isMuted ? " flow-edge-muted" : ""}`,
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color: isMuted ? mutedColor : isPathEdge ? pathColor : baseColor,
      },
      style: {
        stroke: isMuted ? mutedColor : isPathEdge ? pathColor : baseColor,
        strokeWidth: isPathEdge && !isMuted ? 2.5 : 1.5,
      },
      labelStyle: {
        fill: isPathEdge ? pathColor : baseColor,
        fontSize: 10,
        fontWeight: 700,
      },
    };
  });

  return { nodes, edges };
}

export function SecurityGraph({
  graph,
  highlightedPath,
  label,
  nodeTypeFilter = "ALL",
  selectedNodeId = null,
  onNodeSelect,
  theme = "dark",
  viewport,
  onViewportChange,
}: {
  graph: SecurityGraphData;
  highlightedPath: PathEvidence | null;
  label: string;
  nodeTypeFilter?: string;
  selectedNodeId?: string | null;
  onNodeSelect?: (nodeId: string) => void;
  theme?: "light" | "dark";
  viewport?: Viewport;
  onViewportChange?: (viewport: Viewport) => void;
}) {
  const [positions, setPositions] = useState<
    Map<string, { x: number; y: number }> | undefined
  >();
  useEffect(() => {
    const controller = new AbortController();
    setPositions(undefined);
    void requestGraphLayout(graph, controller.signal)
      .then(setPositions)
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setPositions(new Map());
        }
      });
    return () => controller.abort();
  }, [graph]);
  const elements = useMemo(
    () =>
      graphElements(
        graph,
        positions ?? new Map(),
        highlightedPath,
        nodeTypeFilter,
        selectedNodeId,
        theme,
      ),
    [graph, positions, highlightedPath, nodeTypeFilter, selectedNodeId, theme],
  );
  const [nodes, setNodes, onNodesChange] = useNodesState<SecurityFlowNode>(
    elements.nodes,
  );
  const previousGraph = useRef(graph);
  const previousPositions = useRef(positions);
  useEffect(() => {
    const changedRevision = previousGraph.current !== graph;
    const layoutChanged = previousPositions.current !== positions;
    setNodes((current) =>
      reconcileNodePositions(
        current,
        elements.nodes,
        changedRevision || layoutChanged,
      ),
    );
    previousGraph.current = graph;
    previousPositions.current = positions;
  }, [elements.nodes, graph, positions, setNodes]);
  const interactiveNodes = useMemo(
    () =>
      nodes.map((node) => ({
        ...node,
        data: { ...node.data, onInspect: () => onNodeSelect?.(node.id) },
      })),
    [nodes, onNodeSelect],
  );

  if (graph.nodes.length === 0) {
    return (
      <div className="graph-empty">
        No graph nodes were produced for this revision.
      </div>
    );
  }

  if (!positions) {
    return (
      <div className="graph-empty" role="status">
        Preparing graph layout…
      </div>
    );
  }

  return (
    <div className="graph-canvas" role="region" aria-label={label}>
      <ReactFlow
        nodes={interactiveNodes}
        onNodesChange={onNodesChange}
        edges={elements.edges}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.12, minZoom: 0.35, maxZoom: 1.1 }}
        minZoom={0.25}
        maxZoom={1.8}
        nodesConnectable={false}
        nodesFocusable={false}
        edgesFocusable={false}
        viewport={viewport}
        onMoveEnd={(_, nextViewport) => onViewportChange?.(nextViewport)}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={18}
          size={1}
          color={theme === "dark" ? "#352c48" : "#d4c9e9"}
        />
        <Controls showInteractive={false} position="bottom-right" />
      </ReactFlow>
    </div>
  );
}
