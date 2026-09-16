import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import fixtures from "../fixtures.json";
import type { AnalysisResult } from "../types";
import { Overview } from "./Overview";

afterEach(cleanup);

it("replaces an authoritative decision with review when evidence is incomplete", () => {
  const analysis: AnalysisResult = {
    ...(fixtures["safe-change"] as AnalysisResult),
    status: "DEGRADED",
    quality: { confidence: "LOW", coverage_ratio: 0.4, incomplete: true },
    verdict: "ALLOW",
    final_verdict: "ALLOW",
  };

  render(
    <Overview
      analysis={analysis}
      loading={false}
      canRun={true}
      onValidate={vi.fn()}
    />,
  );

  expect(
    screen.getByRole("heading", {
      name: "REVIEW REQUIRED — incomplete evidence",
    }),
  ).toBeInTheDocument();
  expect(screen.getByText("REVIEW")).toBeInTheDocument();
  expect(
    screen.getByText((_, element) =>
      Boolean(
        element?.classList.contains("pipeline-verdicts") &&
        element.textContent?.includes("Preliminary ALLOW"),
      ),
    ),
  ).toBeInTheDocument();
});

it("names the exact finding confirmed by runtime validation", () => {
  const analysis: AnalysisResult = {
    ...(fixtures["authorization-removal"] as AnalysisResult),
    validation_status: "CONFIRMED",
    findings: [
      {
        finding_id: "finding-1",
        route_id: "GET /admin/export",
        method: "GET",
        path: "/admin/export",
        resource: "CustomerExport",
        severity: "CRITICAL",
        validation_capability: "SUPPORTED",
        evidence: ["New anonymous path"],
        validation: {
          status: "CONFIRMED",
          confirmed: true,
          reason_code: "AUTHORIZATION_BYPASS_REPRODUCED",
        },
      },
    ],
  };

  render(
    <Overview
      analysis={analysis}
      loading={false}
      canRun={true}
      onValidate={vi.fn()}
    />,
  );

  expect(
    screen.getByText(
      /anonymous GET \/admin\/export reproduced the expected authorization hypothesis/,
    ),
  ).toBeInTheDocument();
});

it("renders risk bands from backend policy metadata", () => {
  const base = fixtures["safe-change"] as AnalysisResult;
  const analysis: AnalysisResult = {
    ...base,
    risk_result: {
      ...base.risk_result,
      policy: {
        version: "test-policy",
        weights: {},
        review_after: 14,
        block_after: 81,
        review_delta: 21,
        bands: [{ category: "LOW", minimum: 7, maximum: 13 }],
      },
    },
  };

  render(
    <Overview
      analysis={analysis}
      loading={false}
      canRun={true}
      onValidate={vi.fn()}
    />,
  );

  expect(screen.getByText("7–13")).toBeInTheDocument();
  expect(screen.getByText(/policy test-policy/)).toBeInTheDocument();
  expect(screen.queryByText("81–100")).not.toBeInTheDocument();
});
