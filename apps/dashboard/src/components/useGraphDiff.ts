import { useMemo } from "react";
import type {
  AnalysisResult,
  GraphEdge,
  GraphNode,
  PathEvidence,
  SecurityGraph,
} from "../types";

export type DiffFilter = "all" | "changed" | "path";

export function edgeKey(edge: GraphEdge) {
  return `${edge.source_id}|${edge.target_id}|${edge.relationship}`;
}

export function filterGraph(
  graph: SecurityGraph,
  visibleIds: Set<string> | null,
): SecurityGraph {
  if (!visibleIds) return graph;
  const nodes = graph.nodes.filter((node) => visibleIds.has(node.id));
  const ids = new Set(nodes.map((node) => node.id));
  return {
    nodes,
    edges: graph.edges.filter(
      (edge) => ids.has(edge.source_id) && ids.has(edge.target_id),
    ),
  };
}

function countBy<T>(values: T[], key: (value: T) => string) {
  const counts = new Map<string, number>();
  values.forEach((value) => {
    const item = key(value);
    counts.set(item, (counts.get(item) ?? 0) + 1);
  });
  return Array.from(counts, ([type, count]) => ({ type, count })).sort((a, b) =>
    a.type.localeCompare(b.type),
  );
}

function boundedGraph(graph: SecurityGraph, maximumNodes: number) {
  if (graph.nodes.length <= maximumNodes) return graph;
  const nodes = graph.nodes.slice(0, maximumNodes);
  const ids = new Set(nodes.map((node) => node.id));
  return {
    nodes,
    edges: graph.edges.filter(
      (edge) => ids.has(edge.source_id) && ids.has(edge.target_id),
    ),
  };
}

export function useGraphDiff(
  analysis: AnalysisResult,
  diffFilter: DiffFilter,
  selectedPath: PathEvidence | null,
  maximumVisualNodes = 500,
) {
  return useMemo(() => {
    const beforeEdgeKeys = new Set(
      analysis.graph_delta.before.edges.map(edgeKey),
    );
    const afterEdgeKeys = new Set(
      analysis.graph_delta.after.edges.map(edgeKey),
    );
    const beforeNodeIds = new Set(
      analysis.graph_delta.before.nodes.map((node) => node.id),
    );
    const afterNodeIds = new Set(
      analysis.graph_delta.after.nodes.map((node) => node.id),
    );
    const addedEdges = analysis.graph_delta.after.edges.filter(
      (edge) => !beforeEdgeKeys.has(edgeKey(edge)),
    );
    const removedEdges = analysis.graph_delta.before.edges.filter(
      (edge) => !afterEdgeKeys.has(edgeKey(edge)),
    );
    const addedNodes = analysis.graph_delta.after.nodes.filter(
      (node) => !beforeNodeIds.has(node.id),
    );
    const removedNodes = analysis.graph_delta.before.nodes.filter(
      (node) => !afterNodeIds.has(node.id),
    );
    const changedAfterIds = new Set([
      ...addedNodes.map((node) => node.id),
      ...addedEdges.flatMap((edge) => [edge.source_id, edge.target_id]),
    ]);
    const changedBeforeIds = new Set([
      ...removedNodes.map((node) => node.id),
      ...removedEdges.flatMap((edge) => [edge.source_id, edge.target_id]),
    ]);
    const pathNodeIds = new Set(selectedPath?.nodes ?? []);
    const visibleIds = (changed: Set<string>) =>
      diffFilter === "changed"
        ? changed
        : diffFilter === "path"
          ? pathNodeIds
          : null;
    const visibleBefore = boundedGraph(
      filterGraph(analysis.graph_delta.before, visibleIds(changedBeforeIds)),
      maximumVisualNodes,
    );
    const visibleAfter = boundedGraph(
      filterGraph(analysis.graph_delta.after, visibleIds(changedAfterIds)),
      maximumVisualNodes,
    );

    return {
      addedEdges,
      removedEdges,
      addedNodes,
      removedNodes,
      visibleBefore,
      visibleAfter,
      changeCounts: {
        addedEdges: countBy(addedEdges, (edge) => edge.relationship),
        removedEdges: countBy(removedEdges, (edge) => edge.relationship),
        addedNodes: countBy(addedNodes, (node) => node.node_type),
        removedNodes: countBy(removedNodes, (node) => node.node_type),
      },
      afterNodeCounts: countBy(
        analysis.graph_delta.after.nodes,
        (node: GraphNode) => node.node_type,
      ),
    };
  }, [analysis, diffFilter, maximumVisualNodes, selectedPath]);
}
