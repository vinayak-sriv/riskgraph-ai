/// <reference lib="webworker" />

import dagre from "@dagrejs/dagre";
import type { SecurityGraph } from "../types";

const NODE_WIDTH = 240;
const NODE_HEIGHT = 64;

export type GraphLayoutRequest = { requestId: number; graph: SecurityGraph };
export type GraphLayoutResponse = {
  requestId: number;
  positions: Record<string, { x: number; y: number }>;
};

function layoutPositions(graph: SecurityGraph) {
  const layout = new dagre.graphlib.Graph();
  layout.setDefaultEdgeLabel(() => ({}));
  layout.setGraph({
    rankdir: "TB",
    ranksep: 30,
    nodesep: 28,
    marginx: 22,
    marginy: 22,
  });
  graph.nodes.forEach((node) =>
    layout.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT }),
  );
  graph.edges.forEach((edge) => layout.setEdge(edge.source_id, edge.target_id));
  const endpoints = graph.nodes.filter((node) => node.node_type === "ENDPOINT");
  const sources = new Set(graph.edges.map((edge) => edge.source_id));
  graph.nodes
    .filter((node) => node.node_type === "USER" && !sources.has(node.id))
    .forEach((user, index) => {
      const endpoint = endpoints[index] ?? endpoints[0];
      if (endpoint) layout.setEdge(user.id, endpoint.id);
    });
  dagre.layout(layout);
  return Object.fromEntries(
    graph.nodes.map((node) => {
      const position = layout.node(node.id);
      return [
        node.id,
        { x: position.x - NODE_WIDTH / 2, y: position.y - NODE_HEIGHT / 2 },
      ];
    }),
  );
}

self.onmessage = (event: MessageEvent<GraphLayoutRequest>) => {
  const result: GraphLayoutResponse = {
    requestId: event.data.requestId,
    positions: layoutPositions(event.data.graph),
  };
  self.postMessage(result);
};

export {};
