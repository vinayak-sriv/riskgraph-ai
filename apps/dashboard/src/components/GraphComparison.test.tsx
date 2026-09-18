import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import type { ComponentProps } from "react";
import type { SecurityGraph } from "./SecurityGraph";
import type { AnalysisResult } from "../types";
import fixtures from "../fixtures.json";
import { GraphComparison } from "./GraphComparison";

vi.mock("./SecurityGraph", () => ({
  SecurityGraph: (props: ComponentProps<typeof SecurityGraph>) => (
    <div
      role="region"
      aria-label={props.label}
      data-filter={props.nodeTypeFilter}
    >
      {props.graph.nodes.map((node) => (
        <button key={node.id} onClick={() => props.onNodeSelect?.(node.id)}>
          {node.name}
        </button>
      ))}
      <span>{props.highlightedPath?.target}</span>
    </div>
  ),
}));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
const analysis = fixtures["authorization-removal"] as AnalysisResult;

it("groups graph controls and exposes the attack path as an ordered sequence", () => {
  render(
    <GraphComparison analysis={analysis} theme="dark" onNotify={vi.fn()} />,
  );

  const filters = screen.getByRole("group", { name: "Graph filters" });
  expect(filters).toContainElement(
    within(filters).getByRole("combobox", { name: "Graph changes" }),
  );
  expect(
    screen.getByRole("group", { name: "Graph view controls" }),
  ).toContainElement(screen.getByRole("button", { name: "Compare" }));
  const path = screen.getByRole("list", {
    name: "Newly reachable attack path",
  });
  expect(within(path).getAllByRole("listitem")).toHaveLength(6);
  expect(within(path).getByRole("button", { name: "Anonymous" })).toBeVisible();
  expect(within(path).getByRole("button", { name: "Customer" })).toBeVisible();
});

it("inspects both revisions without marking the before graph as a new attack path", () => {
  render(
    <GraphComparison analysis={analysis} theme="dark" onNotify={vi.fn()} />,
  );
  const before = screen.getByRole("region", { name: /Before change/ });
  fireEvent.click(
    within(before).getByRole("button", { name: "GET /admin/export" }),
  );
  expect(screen.getByRole("dialog")).toHaveTextContent("Before graph");
  expect(screen.getByRole("dialog")).toHaveTextContent(
    "Outside new attack path",
  );
  expect(screen.getByRole("dialog")).toHaveTextContent("Requires Role");
  const close = screen.getByRole("button", { name: "Close inspector" });
  expect(close).toHaveFocus();
  fireEvent.keyDown(close, { key: "Tab" });
  expect(close).toHaveFocus();
  fireEvent.keyDown(close, { key: "Escape" });
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  fireEvent.click(
    within(screen.getByRole("region", { name: /After change/ })).getByRole(
      "button",
      { name: "GET /admin/export" },
    ),
  );
  expect(screen.getByRole("dialog")).toHaveTextContent("On new attack path");
  expect(screen.getByRole("dialog")).toHaveTextContent(
    "FromAnonymousCan Access",
  );
});

it("closes only the node inspector on the first Escape in full-screen mode", () => {
  render(
    <GraphComparison analysis={analysis} theme="dark" onNotify={vi.fn()} />,
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Open full-screen graph" }),
  );
  fireEvent.click(
    within(screen.getByRole("region", { name: /After change/ })).getByRole(
      "button",
      { name: "GET /admin/export" },
    ),
  );

  const inspector = screen.getByRole("dialog", {
    name: "GET /admin/export",
  });
  fireEvent.keyDown(inspector, { key: "Escape" });

  expect(
    screen.queryByRole("dialog", { name: "GET /admin/export" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "Exit full-screen graph" }),
  ).toBeInTheDocument();
});

it("preserves graph modes and highlights and resets stale inspection on scenario change", () => {
  const view = render(
    <GraphComparison analysis={analysis} theme="dark" onNotify={vi.fn()} />,
  );
  fireEvent.change(screen.getByLabelText("Highlight"), {
    target: { value: "ENDPOINT" },
  });
  expect(screen.getByRole("region", { name: /After change/ })).toHaveAttribute(
    "data-filter",
    "ENDPOINT",
  );
  fireEvent.click(screen.getByRole("button", { name: "After" }));
  expect(
    screen.queryByRole("region", { name: /Before change/ }),
  ).not.toBeInTheDocument();
  fireEvent.click(
    within(screen.getByRole("region", { name: /After change/ })).getByRole(
      "button",
      { name: "GET /admin/export" },
    ),
  );
  view.rerender(
    <GraphComparison
      analysis={fixtures["safe-change"] as AnalysisResult}
      theme="light"
      onNotify={vi.fn()}
    />,
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByLabelText("Highlight")).toHaveValue("ALL");
  expect(
    screen.queryByRole("button", { name: "Copy attack path" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Compare" }));
  expect(
    screen.getByRole("region", { name: /Before change/ }),
  ).toBeInTheDocument();
});

it("selects and copies each server-provided path using the original node IDs", async () => {
  const otherPath = {
    ...analysis.graph_delta.new_paths[0],
    target: "resource:Other",
    nodes: ["user:anonymous", "resource:Other"],
  };
  const multiple = {
    ...analysis,
    graph_delta: {
      ...analysis.graph_delta,
      new_paths: [...analysis.graph_delta.new_paths, otherPath],
    },
  };
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { clipboard: { writeText } });
  const notify = vi.fn();
  render(
    <GraphComparison analysis={multiple} theme="dark" onNotify={notify} />,
  );
  fireEvent.change(screen.getByLabelText("Attack path"), {
    target: { value: "1" },
  });
  expect(
    screen.getByRole("region", { name: /After change/ }),
  ).toHaveTextContent("resource:Other");
  fireEvent.click(screen.getByRole("button", { name: "Copy attack path" }));
  await screen.findByTitle("Copy attack path");
  expect(writeText).toHaveBeenCalledWith("user:anonymous -> resource:Other");
});

it("does not mount an oversized graph until focused detail is requested", () => {
  const nodes = Array.from({ length: 501 }, (_, index) => ({
    id: `function:${index}`,
    node_type: "FUNCTION",
    name: `Function ${index}`,
  }));
  const largeAnalysis: AnalysisResult = {
    ...(fixtures["safe-change"] as AnalysisResult),
    graph_delta: {
      before: { nodes, edges: [] },
      after: { nodes, edges: [] },
      new_paths: [],
      removed_paths: [],
    },
  };

  render(
    <GraphComparison
      analysis={largeAnalysis}
      theme="dark"
      onNotify={vi.fn()}
    />,
  );

  expect(screen.getByText("Summary graph mode")).toBeInTheDocument();
  expect(
    screen.queryByRole("region", { name: /After change/ }),
  ).not.toBeInTheDocument();
  expect(screen.getByLabelText("Node clusters by type")).toHaveTextContent(
    "Function501",
  );

  fireEvent.click(
    screen.getByRole("button", { name: "Load focused graph detail" }),
  );
  expect(
    screen.getByRole("region", { name: /After change/ }),
  ).toBeInTheDocument();
  expect(screen.getByText("Page 1 of 6")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  expect(screen.getByText("Page 2 of 6")).toBeInTheDocument();
});

it("defaults medium graphs to one focused revision", () => {
  const base = fixtures["safe-change"] as AnalysisResult;
  const nodes = [
    ...base.graph_delta.after.nodes,
    ...Array.from(
      { length: 101 - base.graph_delta.after.nodes.length },
      (_, index) => ({
        id: `function:${index}`,
        node_type: "FUNCTION",
        name: `Function ${index}`,
      }),
    ),
  ];
  const mediumAnalysis: AnalysisResult = {
    ...base,
    graph_delta: {
      ...base.graph_delta,
      before: { nodes, edges: base.graph_delta.before.edges },
      after: { nodes, edges: base.graph_delta.after.edges },
    },
  };

  render(
    <GraphComparison
      analysis={mediumAnalysis}
      theme="dark"
      onNotify={vi.fn()}
    />,
  );

  expect(screen.getByText("Focused graph mode")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Compare" })).toBeDisabled();
  expect(
    screen.queryByRole("region", { name: /Before change/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("region", { name: /After change/ }),
  ).toBeInTheDocument();
});
