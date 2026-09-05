import { useMemo } from "react";
import dagre from "@dagrejs/dagre";
import { Box, Database, KeyRound, Route, Server, UserRound } from "lucide-react";
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  type Edge,
  type Node,
  type NodeProps
} from "@xyflow/react";
import type { PathEvidence, SecurityGraph as SecurityGraphData } from "../types";

const NODE_WIDTH = 204;
const NODE_HEIGHT = 64;

type GraphNodeData = Record<string, unknown> & { name: string; nodeType: string };
type SecurityFlowNode = Node<GraphNodeData, "securityNode">;

const nodeTypes = { securityNode: SecurityNode };

function SecurityNode({ data }: NodeProps<SecurityFlowNode>) {
  const Icon = data.nodeType === "USER" ? UserRound
    : data.nodeType === "ROLE" ? KeyRound
      : data.nodeType === "ENDPOINT" ? Route
        : data.nodeType === "DATABASE" || data.nodeType === "DATA_RESOURCE" ? Database
          : data.nodeType === "EXTERNAL_SERVICE" ? Server
            : Box;
  return (
    <div className="security-node-content">
      <Handle type="target" position={Position.Top} />
      <span className="security-node-icon"><Icon size={15} /></span>
      <div><small>{data.nodeType.replaceAll("_", " ")}</small><strong>{data.name}</strong></div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

function layoutGraph(
  graph: SecurityGraphData,
  highlightedPath: PathEvidence | null,
  nodeTypeFilter: string,
  selectedNodeId: string | null,
  theme: "light" | "dark"
) {
  const layout = new dagre.graphlib.Graph();
  layout.setDefaultEdgeLabel(() => ({}));
  layout.setGraph({ rankdir: "TB", ranksep: 30, nodesep: 28, marginx: 22, marginy: 22 });

  graph.nodes.forEach((node) => layout.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT }));
  graph.edges.forEach((edge) => layout.setEdge(edge.source_id, edge.target_id));
  const endpoints = graph.nodes.filter((node) => node.node_type === "ENDPOINT");
  graph.nodes.filter((node) => node.node_type === "USER" && !graph.edges.some((edge) => edge.source_id === node.id))
    .forEach((user, index) => {
      const endpoint = endpoints[index] ?? endpoints[0];
      if (endpoint) layout.setEdge(user.id, endpoint.id);
    });
  dagre.layout(layout);

  const pathPairs = new Set<string>();
  highlightedPath?.nodes.slice(0, -1).forEach((source, index) => {
    pathPairs.add(`${source}|${highlightedPath.nodes[index + 1]}`);
  });

  const nodes: SecurityFlowNode[] = graph.nodes.map((node) => {
    const position = layout.node(node.id);
    const isPathNode = highlightedPath?.nodes.includes(node.id) ?? false;
    const isMuted = nodeTypeFilter !== "ALL" && node.node_type !== nodeTypeFilter;
    return {
      id: node.id,
      position: { x: position.x - NODE_WIDTH / 2, y: position.y - NODE_HEIGHT / 2 },
      type: "securityNode",
      data: { name: node.name, nodeType: node.node_type },
      className: `flow-node flow-node-${node.node_type.toLowerCase()}${isPathNode ? " flow-node-path" : ""}${isMuted ? " flow-node-muted" : ""}${selectedNodeId === node.id ? " flow-node-selected" : ""}`,
      ariaLabel: `${node.node_type.replaceAll("_", " ")}: ${node.name}`,
      draggable: true,
      selectable: true,
      style: { width: NODE_WIDTH, height: NODE_HEIGHT }
    };
  });

  const edges: Edge[] = graph.edges.map((edge, index) => {
    const isPathEdge = pathPairs.has(`${edge.source_id}|${edge.target_id}`);
    const source = graph.nodes.find((node) => node.id === edge.source_id);
    const target = graph.nodes.find((node) => node.id === edge.target_id);
    const isMuted = nodeTypeFilter !== "ALL" && source?.node_type !== nodeTypeFilter && target?.node_type !== nodeTypeFilter;
    const baseColor = theme === "dark" ? "#9aa89f" : "#7f8982";
    const mutedColor = theme === "dark" ? "#4b5850" : "#cbd2cd";
    const pathColor = theme === "dark" ? "#f06b62" : "#c83c32";
    return {
      id: `${edge.source_id}-${edge.target_id}-${index}`,
      source: edge.source_id,
      target: edge.target_id,
      type: "smoothstep",
      pathOptions: { borderRadius: 10, offset: 18 },
      label: edge.relationship.replaceAll("_", " "),
      animated: isPathEdge,
      className: `${isPathEdge ? "flow-edge-path" : ""}${isMuted ? " flow-edge-muted" : ""}`,
      markerEnd: { type: MarkerType.ArrowClosed, color: isMuted ? mutedColor : isPathEdge ? pathColor : baseColor },
      style: { stroke: isMuted ? mutedColor : isPathEdge ? pathColor : baseColor, strokeWidth: isPathEdge && !isMuted ? 2.5 : 1.5 },
      labelStyle: { fill: isPathEdge ? pathColor : baseColor, fontSize: 10, fontWeight: 700 }
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
  theme = "light"
}: {
  graph: SecurityGraphData;
  highlightedPath: PathEvidence | null;
  label: string;
  nodeTypeFilter?: string;
  selectedNodeId?: string | null;
  onNodeSelect?: (nodeId: string) => void;
  theme?: "light" | "dark";
}) {
  const elements = useMemo(
    () => layoutGraph(graph, highlightedPath, nodeTypeFilter, selectedNodeId, theme),
    [graph, highlightedPath, nodeTypeFilter, selectedNodeId, theme]
  );

  if (graph.nodes.length === 0) {
    return <div className="graph-empty">No graph nodes were produced for this revision.</div>;
  }

  return (
    <div className="graph-canvas" role="img" aria-label={label}>
      <ReactFlow
        nodes={elements.nodes}
        edges={elements.edges}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2, minZoom: 0.35, maxZoom: 1.1 }}
        minZoom={0.25}
        maxZoom={1.8}
        nodesConnectable={false}
        onNodeClick={(_, node) => onNodeSelect?.(node.id)}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={18} size={1} color={theme === "dark" ? "#3b493f" : "#dfe4df"} />
        <Controls showInteractive={false} position="bottom-right" />
      </ReactFlow>
    </div>
  );
}
