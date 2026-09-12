import {
  fireEvent,
  render,
  screen,
  waitFor,
  cleanup,
  within,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import fixtures from "./fixtures.json";
import App from "./App";
vi.mock("./components/SecurityGraph", () => ({
  SecurityGraph: () => <div>Graph visualization</div>,
}));
afterEach(() => {
  cleanup();
  window.history.replaceState(null, "", "/");
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function openView(
  name: "Analysis" | "New analysis" | "Saved scans" | "Account",
) {
  const hash =
    name === "Analysis"
      ? "analysis"
      : name === "New analysis"
        ? "new-analysis"
        : name === "Saved scans"
          ? "saved-scans"
          : "account";
  const navigation = screen.getByRole("navigation", {
    name: "Workspace navigation",
  });
  const link = within(navigation).getByRole("link", { name });
  expect(link).toHaveAttribute("href", `#${hash}`);
  fireEvent.click(link);
  const heading =
    name === "Analysis"
      ? "Security analysis"
      : name === "New analysis"
        ? "Compare source revisions"
        : name === "Saved scans"
          ? "Open previous analysis"
          : "Identity and connections";
  await screen.findByRole("heading", { name: heading });
}

function authResult(url: string) {
  if (url.endsWith("/auth/session"))
    return {
      authenticated: true,
      user: { username: "admin", name: "Test admin", role: "ADMIN" },
      github: {
        status: "CONNECTED",
        login: "octocat",
        permissions: ["Identity verified"],
      },
    };
  if (url.endsWith("/auth/csrf"))
    return { headerName: "X-CSRF-TOKEN", token: "test-csrf" };
  return null;
}

it("switches to a safe result without retaining blocked-change language", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        authResult(url) ??
        (url.includes("safe-change")
          ? fixtures["safe-change"]
          : fixtures["authorization-removal"]),
    })),
  );
  render(<App />);
  await waitFor(() =>
    expect(screen.getByLabelText("Demo scenario")).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Demo scenario"), {
    target: { value: "safe-change" },
  });
  await screen.findByText("Decision: ALLOW");
  expect(
    screen.queryByText("Why this change is blocked"),
  ).not.toBeInTheDocument();
});

it("sends immutable source input and shows failure honestly", async () => {
  const fetcher = vi.fn(async (_url: string, options?: RequestInit) =>
    options?.method === "POST"
      ? {
          ok: false,
          json: async () => ({
            code: "DEPENDENCY_UNAVAILABLE",
            message: "Analyzer unavailable",
          }),
        }
      : {
          ok: true,
          json: async () =>
            authResult(_url) ?? fixtures["authorization-removal"],
        },
  );
  vi.stubGlobal("fetch", fetcher);
  render(<App />);
  await openView("New analysis");
  fillSource();
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Run Analysis" })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole("button", { name: "Run Analysis" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Source analysis failed",
  );
  const call = fetcher.mock.calls.find(
    ([, options]) => options?.method === "POST",
  );
  expect(JSON.parse(call![1]!.body as string)).toEqual({
    repository_path: "/allowlisted/repo",
    old_commit: "a".repeat(40),
    new_commit: "b".repeat(40),
  });
  expect(call![1]!.credentials).toBe("include");
  expect(new Headers(call![1]!.headers).get("X-CSRF-TOKEN")).toBe("test-csrf");
});

it("keeps source mutation controls disabled for Developers", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        url.endsWith("/auth/session")
          ? {
              authenticated: true,
              user: { username: "dev", name: "Read only", role: "DEVELOPER" },
              github: {
                status: "CONNECTED",
                login: "reader",
                permissions: ["Identity verified"],
              },
            }
          : fixtures["authorization-removal"],
    })),
  );
  render(<App />);
  await openView("New analysis");
  expect(screen.getByRole("button", { name: "Run Analysis" })).toBeDisabled();
  await openView("Saved scans");
  fireEvent.change(screen.getByLabelText("Saved scan ID"), {
    target: { value: "d".repeat(64) },
  });
  expect(screen.getByRole("button", { name: "Open scan" })).toBeEnabled();
  await openView("Account");
  expect(screen.queryByText("Manage accounts")).not.toBeInTheDocument();
});

it("replaces protected service interfaces with a GitHub connection gate", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        url.endsWith("/auth/session")
          ? {
              authenticated: false,
              github: { status: "DISCONNECTED", permissions: [] },
            }
          : fixtures["authorization-removal"],
    })),
  );
  render(<App />);
  await openView("New analysis");
  expect(
    screen.getByText("Connect your GitHub account to access this page"),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("Repository path")).not.toBeInTheDocument();
  await openView("Saved scans");
  expect(
    screen.getByText("Connect your GitHub account to access this page"),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("Saved scan ID")).not.toBeInTheDocument();
  await openView("Account");
  expect(
    screen.getByRole("link", { name: /Continue with GitHub/ }),
  ).toHaveAttribute("href", "http://localhost:8080/auth/github/connect");
});

it("labels saved fixtures when the platform is unavailable", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
  render(<App />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "saved deterministic fixture",
  );
  expect(screen.getByText(/Validation: NOT_RUN/)).toBeInTheDocument();
});

const sourceResult = {
  ...fixtures["authorization-removal"],
  scan_id: "c".repeat(64),
  provenance: {
    repository_identity: "local:test",
    repository_path: "/allowlisted/repo",
    old_commit: "a".repeat(40),
    new_commit: "b".repeat(40),
  },
  analyzer_version: "test-analyzer",
};

function fillSource() {
  fireEvent.change(screen.getByLabelText("Repository path"), {
    target: { value: "/allowlisted/repo" },
  });
  fireEvent.change(screen.getByLabelText("Old commit SHA"), {
    target: { value: "a".repeat(40) },
  });
  fireEvent.change(screen.getByLabelText("New commit SHA"), {
    target: { value: "b".repeat(40) },
  });
}

it("refreshes the displayed source revisions instead of unsaved form edits", async () => {
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => ({
    ok: true,
    json: async () =>
      authResult(url) ??
      (options?.method === "POST"
        ? sourceResult
        : fixtures["authorization-removal"]),
  }));
  vi.stubGlobal("fetch", fetcher);
  render(<App />);
  await openView("New analysis");
  fillSource();
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Run Analysis" })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole("button", { name: "Run Analysis" }));
  await screen.findByText("Repository analysis");
  await openView("New analysis");
  fireEvent.change(screen.getByLabelText("Repository path"), {
    target: { value: "/unfinished/draft" },
  });
  await openView("Analysis");
  fireEvent.click(screen.getByRole("button", { name: "Refresh analysis" }));
  await waitFor(() =>
    expect(
      fetcher.mock.calls.filter(([, options]) => options?.method === "POST"),
    ).toHaveLength(2),
  );
  const posts = fetcher.mock.calls.filter(
    ([, options]) => options?.method === "POST",
  );
  expect(JSON.parse(posts[1][1]!.body as string)).toEqual({
    repository_path: "/allowlisted/repo",
    old_commit: "a".repeat(40),
    new_commit: "b".repeat(40),
  });
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "Refresh analysis" }),
    ).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Demo scenario"), {
    target: { value: "authorization-removal" },
  });
  await screen.findByText("Spring Boot demo");
});

it("opens a saved scan for a Developer without enabling mutations", async () => {
  const fetcher = vi.fn(async (url: string) => ({
    ok: true,
    json: async () =>
      url.endsWith("/auth/session")
        ? {
            authenticated: true,
            user: { username: "dev", name: "Reader", role: "DEVELOPER" },
            github: {
              status: "CONNECTED",
              login: "reader",
              permissions: ["Identity verified"],
            },
          }
        : url.includes("/analyses/")
          ? sourceResult
          : fixtures["authorization-removal"],
  }));
  vi.stubGlobal("fetch", fetcher);
  render(<App />);
  await openView("Saved scans");
  fireEvent.change(screen.getByLabelText("Saved scan ID"), {
    target: { value: sourceResult.scan_id },
  });
  fireEvent.click(screen.getByRole("button", { name: "Open scan" }));
  await screen.findByText("Repository analysis");
  expect(fetcher).toHaveBeenCalledWith(
    expect.stringContaining(`/analyses/${sourceResult.scan_id}`),
    expect.objectContaining({ credentials: "include" }),
  );
  expect(
    screen.getByRole("button", { name: "Refresh analysis" }),
  ).toBeDisabled();
  expect(
    screen.getByRole("button", { name: "Validate registered Docker sandbox" }),
  ).toBeDisabled();
});

it("uses the final policy verdict after validation and retains the preliminary verdict", async () => {
  const validated = {
    ...sourceResult,
    final_verdict: "REVIEW",
    pre_validation_verdict: "BLOCK",
    validation_status: "REJECTED",
    validation: { status: "REJECTED", reason_code: "NO_EXPOSURE_CONFIRMED" },
  };
  const fetcher = vi.fn(async (url: string, _options?: RequestInit) => ({
    ok: true,
    json: async () =>
      authResult(url) ??
      (url.endsWith("/validation")
        ? validated
        : url.includes("/analyses/")
          ? sourceResult
          : fixtures["authorization-removal"]),
  }));
  vi.stubGlobal("fetch", fetcher);
  render(<App />);
  await openView("Saved scans");
  fireEvent.change(screen.getByLabelText("Saved scan ID"), {
    target: { value: sourceResult.scan_id },
  });
  fireEvent.click(screen.getByRole("button", { name: "Open scan" }));
  const validate = await screen.findByRole("button", {
    name: "Validate registered Docker sandbox",
  });
  fireEvent.click(validate);
  await screen.findByRole("heading", {
    name: "This change needs a closer look",
  });
  expect(
    screen.getByRole("heading", { name: "Decision: REVIEW" }),
  ).toBeInTheDocument();
  expect(screen.getByText(/Preliminary/)).toHaveTextContent(
    /Preliminary\s*BLOCK\s*Final\s*REVIEW/,
  );
  const call = fetcher.mock.calls.find(([url]) => url.endsWith("/validation"));
  expect(call![0]).toContain(`/analyses/${sourceResult.scan_id}/validation`);
  expect(call![1]).toMatchObject({ method: "POST", credentials: "include" });
  expect(new Headers(call![1]!.headers).get("X-CSRF-TOKEN")).toBe("test-csrf");
});

it("does not restore a protected result when an in-flight request finishes after sign-out", async () => {
  let finish!: (value: unknown) => void;
  const pending = new Promise((resolve) => {
    finish = resolve;
  });
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/analyses") && options?.method === "POST") return pending;
    return {
      ok: true,
      json: async () => authResult(url) ?? fixtures["authorization-removal"],
    };
  });
  vi.stubGlobal("fetch", fetcher);
  render(<App />);
  await openView("New analysis");
  fillSource();
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Run Analysis" })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole("button", { name: "Run Analysis" }));
  await waitFor(() =>
    expect(fetcher.mock.calls.some(([url]) => url.endsWith("/analyses"))).toBe(
      true,
    ),
  );
  await openView("Account");
  fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
  await screen.findByRole("button", { name: "Sign in" });
  const call = fetcher.mock.calls.find(([url]) => url.endsWith("/analyses"));
  expect(call![1]!.signal!.aborted).toBe(true);
  finish({ ok: true, json: async () => sourceResult });
  await openView("New analysis");
  await screen.findByText("Connect your GitHub account to access this page");
  expect(screen.queryByLabelText("Repository path")).not.toBeInTheDocument();
  await openView("Analysis");
  expect(screen.queryByText("Repository analysis")).not.toBeInTheDocument();
});

it.each([
  "new-public-sensitive-endpoint",
  "sensitive-resource-exposure",
] as const)("keeps %s risk and path data intact", async (name) => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        authResult(url) ??
        (url.endsWith(name)
          ? fixtures[name]
          : fixtures["authorization-removal"]),
    })),
  );
  render(<App />);
  await waitFor(() =>
    expect(screen.getByLabelText("Demo scenario")).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Demo scenario"), {
    target: { value: name },
  });
  await waitFor(() =>
    expect(document.title).not.toContain("Authorization Removal"),
  );
  expect(screen.getByRole("meter", { name: "Before risk" })).toHaveAttribute(
    "aria-valuenow",
    String(fixtures[name].risk_result.risk_before),
  );
  expect(screen.getByRole("meter", { name: "After risk" })).toHaveAttribute(
    "aria-valuenow",
    String(fixtures[name].risk_result.risk_after),
  );
  expect(
    screen.getByRole("button", { name: "Copy attack path" }),
  ).toBeInTheDocument();
});

it("exports the complete result payload without dropping server fields", async () => {
  const result = {
    ...fixtures["safe-change"],
    additional_server_evidence: { retained: true },
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () => authResult(url) ?? result,
    })),
  );
  const blob = vi.fn();
  vi.stubGlobal(
    "Blob",
    class {
      constructor(parts: unknown[], options: unknown) {
        blob(parts, options);
      }
    },
  );
  const create = vi.fn(() => "blob:analysis");
  const revoke = vi.fn();
  vi.stubGlobal("URL", { createObjectURL: create, revokeObjectURL: revoke });
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => {});
  render(<App />);
  await screen.findByRole("heading", { name: "Decision: ALLOW" });
  fireEvent.click(screen.getByRole("button", { name: "Export JSON" }));
  expect(JSON.parse(blob.mock.calls[0][0][0])).toEqual(result);
  expect(blob.mock.calls[0][1]).toEqual({ type: "application/json" });
  expect(click).toHaveBeenCalledOnce();
  expect(revoke).toHaveBeenCalledWith("blob:analysis");
});

it("keeps source locations, extraction diagnostics, and AI hypotheses distinct", async () => {
  const result = {
    ...sourceResult,
    source_evidence: {
      after: [
        {
          endpoint: { method: "GET", endpoint: "/admin/export" },
          source_location: {
            path: "src/AdminExportController.java",
            start_line: 12,
            end_line: 20,
          },
          extraction_confidence: { overall: "MEDIUM" },
        },
      ],
    },
    diagnostics: [
      {
        severity: "WARNING",
        code: "UNRESOLVED_CALL",
        message: "A call could not be resolved.",
        path: "src/AdminExportController.java",
      },
    ],
    ai: {
      status: "AVAILABLE",
      analysis: {
        finding: "Authorization Bypass",
        hypothesis: "Unauthenticated customer access",
        recommended_test: "GET /admin/export without authentication",
        confidence: "HIGH",
      },
    },
    quality: { confidence: "MEDIUM", coverage_ratio: 0.8, incomplete: true },
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        authResult(url) ??
        (url.includes("/analyses/")
          ? result
          : fixtures["authorization-removal"]),
    })),
  );
  render(<App />);
  await openView("Saved scans");
  fireEvent.change(screen.getByLabelText("Saved scan ID"), {
    target: { value: sourceResult.scan_id },
  });
  fireEvent.click(screen.getByRole("button", { name: "Open scan" }));
  expect(
    await screen.findByRole("table", { name: /After revision/ }),
  ).toHaveTextContent(/src\/AdminExportController.java:12.*20/);
  expect(
    screen.getByRole("heading", { name: "Extraction diagnostics" }),
  ).toBeInTheDocument();
  expect(screen.getByText("A call could not be resolved.")).toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "AI explanation · unconfirmed" }),
  ).toBeInTheDocument();
  expect(
    screen.getByText("Unauthenticated customer access"),
  ).toBeInTheDocument();
  expect(
    screen.getByText("GET /admin/export without authentication"),
  ).toBeInTheDocument();
  expect(screen.getByText("80% call-chain coverage")).toBeInTheDocument();
});
