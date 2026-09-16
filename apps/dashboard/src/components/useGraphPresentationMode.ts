import { useMemo } from "react";
import type { AnalysisResult } from "../types";

export type GraphPresentationMode = "NORMAL" | "FOCUSED" | "SUMMARY";

export type GraphPresentation = {
  mode: GraphPresentationMode;
  largestRevisionNodes: number;
  totalNodes: number;
  allowCompare: boolean;
  requiresDetailedLoad: boolean;
};

export function graphPresentationFor(
  analysis: AnalysisResult,
): GraphPresentation {
  const before = analysis.graph_delta.before.nodes.length;
  const after = analysis.graph_delta.after.nodes.length;
  const largestRevisionNodes = Math.max(before, after);
  const totalNodes = before + after;

  if (largestRevisionNodes <= 100) {
    return {
      mode: "NORMAL",
      largestRevisionNodes,
      totalNodes,
      allowCompare: true,
      requiresDetailedLoad: false,
    };
  }
  if (largestRevisionNodes <= 500) {
    return {
      mode: "FOCUSED",
      largestRevisionNodes,
      totalNodes,
      allowCompare: false,
      requiresDetailedLoad: false,
    };
  }
  return {
    mode: "SUMMARY",
    largestRevisionNodes,
    totalNodes,
    allowCompare: false,
    requiresDetailedLoad: true,
  };
}

export function useGraphPresentationMode(analysis: AnalysisResult) {
  return useMemo(() => graphPresentationFor(analysis), [analysis]);
}
