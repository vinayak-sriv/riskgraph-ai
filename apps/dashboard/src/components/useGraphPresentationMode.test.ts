import { expect, it } from "vitest";
import type { AnalysisResult, GraphNode } from "../types";
import fixtures from "../fixtures.json";
import { graphPresentationFor } from "./useGraphPresentationMode";

function analysisWithNodeCount(count: number): AnalysisResult {
  const base = fixtures["safe-change"] as AnalysisResult;
  const nodes: GraphNode[] = Array.from({ length: count }, (_, index) => ({
    id: `function:${index}`,
    node_type: "FUNCTION",
    name: `Function ${index}`,
  }));
  return {
    ...base,
    graph_delta: {
      ...base.graph_delta,
      before: { nodes, edges: [] },
      after: { nodes, edges: [] },
    },
  };
}

it.each([
  [100, "NORMAL", true, false],
  [101, "FOCUSED", false, false],
  [500, "FOCUSED", false, false],
  [501, "SUMMARY", false, true],
  [1_000, "SUMMARY", false, true],
  [2_000, "SUMMARY", false, true],
] as const)(
  "selects the bounded graph mode at %i nodes",
  (count, mode, allowCompare, requiresDetailedLoad) => {
    expect(graphPresentationFor(analysisWithNodeCount(count))).toMatchObject({
      mode,
      allowCompare,
      requiresDetailedLoad,
      largestRevisionNodes: count,
    });
  },
);
