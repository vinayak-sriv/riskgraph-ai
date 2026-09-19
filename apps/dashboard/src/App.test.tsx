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
  name: "Analysis" | "New analysis" | "Open scan by ID" | "Account",
) {
  const hash =
    name === "Analysis"
      ? "analysis"
      : name === "New analysis"
        ? "new-analysis"
        : name === "Open scan by ID"
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
        : name === "Open scan by ID"
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
      github_connection_required: true,
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
    repository: "/allowlisted/repo",
    old_commit: "a".repeat(40),
    new_commit: "b".repeat(40),
  });
  expect(call![1]!.credentials).toBe("include");
  expect(new Headers(call![1]!.headers).get("X-CSRF-TOKEN")).toBe("test-csrf");
});

it("shows a loading indicator while a source analysis runs", async () => {
  let resolvePost: () => void = () => {};
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    if (options?.method === "POST" && url.endsWith("/scans")) {
      return new Promise((resolve) => {
        resolvePost = () =>
          resolve({
            ok: true,
            json: async () => fixtures["authorization-removal"],
          });
      });
    }
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
  expect(
    await screen.findByText("Submitting source analysis…"),
  ).toBeInTheDocument();
  resolvePost();
  await waitFor(() =>
    expect(
      screen.queryByText("Submitting source analysis…"),
    ).not.toBeInTheDocument(),
  );
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
  await openView("Open scan by ID");
  fireEvent.change(screen.getByLabelText("Saved scan ID"), {
    target: { value: "d".repeat(64) },
  });
  expect(screen.getByRole("button", { name: "Open scan" })).toBeEnabled();
  await openView("Account");
  expect(screen.queryByText("Manage accounts")).not.toBeInTheDocument();
});

it("requires platform sign-in before showing protected service interfaces", async () => {
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
  expect(screen.getByText("Sign in to access this page")).toBeInTheDocument();
  expect(screen.queryByLabelText("Repository path")).not.toBeInTheDocument();
  await openView("Open scan by ID");
  expect(screen.getByText("Sign in to access this page")).toBeInTheDocument();
  expect(screen.queryByLabelText("Saved scan ID")).not.toBeInTheDocument();
  await openView("Account");
  expect(
    screen.getByRole("link", { name: /Continue with GitHub/ }),
  ).toHaveAttribute("href", "http://localhost:8080/auth/github/connect");
});

it("requires GitHub when the backend capability says it is mandatory", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        url.endsWith("/auth/session")
          ? {
              authenticated: true,
              user: {
                username: "analyst",
                name: "Connected-mode analyst",
                role: "ANALYST",
              },
              github: { status: "DISCONNECTED", permissions: [] },
              github_connection_required: true,
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
  await openView("Open scan by ID");
  expect(
    screen.getByText("Connect your GitHub account to access this page"),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("Saved scan ID")).not.toBeInTheDocument();
});

it("fails closed when the backend omits the GitHub requirement capability", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        url.endsWith("/auth/session")
          ? {
              authenticated: true,
              user: {
                username: "analyst",
                name: "Legacy-response analyst",
                role: "ANALYST",
              },
              github: { status: "NOT_CONFIGURED", permissions: [] },
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
  await openView("Open scan by ID");
  expect(screen.queryByLabelText("Saved scan ID")).not.toBeInTheDocument();
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

it("loads membership-filtered scan history and opens a completed result", async () => {
  const history = {
    items: [
      {
        job_id: "11111111-1111-1111-1111-111111111111",
        scan_id: sourceResult.scan_id,
        repository: "local:history-repository",
        old_commit: "a".repeat(40),
        new_commit: "b".repeat(40),
        status: "COMPLETED",
        risk_delta: 69,
        verdict: "BLOCK",
        updated_at: "2026-09-16T00:00:00Z",
      },
    ],
    next_cursor: null,
  };
  const fetcher = vi.fn(async (url: string) => ({
    ok: true,
    json: async () =>
      authResult(url) ??
      (url.includes("/scans?")
        ? history
        : url.includes(`/analyses/${sourceResult.scan_id}`)
          ? sourceResult
          : fixtures["authorization-removal"]),
  }));
  vi.stubGlobal("fetch", fetcher);

  render(<App />);
  await openView("Open scan by ID");
  await screen.findByText("local:history-repository");
  expect(screen.getByText("COMPLETED")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "View" }));
  await screen.findByText("Repository analysis");
  expect(fetcher).toHaveBeenCalledWith(
    expect.stringContaining(`/analyses/${sourceResult.scan_id}`),
    expect.objectContaining({ credentials: "include" }),
  );
});

it("shows a retry when loading another scan-history page fails", async () => {
  let nextPageAttempts = 0;
  const firstItem = {
    job_id: "11111111-1111-1111-1111-111111111111",
    scan_id: sourceResult.scan_id,
    repository: "local:first-page",
    old_commit: "a".repeat(40),
    new_commit: "b".repeat(40),
    status: "COMPLETED",
    risk_delta: 69,
    verdict: "BLOCK",
    updated_at: "2026-09-16T00:00:00Z",
  };
  const secondItem = {
    ...firstItem,
    job_id: "22222222-2222-2222-2222-222222222222",
    repository: "local:second-page",
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const auth = authResult(url);
      if (auth) return { ok: true, status: 200, json: async () => auth };
      if (url.includes("cursor=next-page")) {
        nextPageAttempts += 1;
        return nextPageAttempts === 1
          ? {
              ok: false,
              status: 503,
              json: async () => ({ code: "HISTORY_UNAVAILABLE" }),
            }
          : {
              ok: true,
              status: 200,
              json: async () => ({ items: [secondItem], next_cursor: null }),
            };
      }
      if (url.includes("/scans?")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            items: [firstItem],
            next_cursor: "next-page",
          }),
        };
      }
      return {
        ok: true,
        status: 200,
        json: async () => fixtures["authorization-removal"],
      };
    }),
  );

  render(<App />);
  await openView("Open scan by ID");
  await screen.findByText("local:first-page");
  fireEvent.click(screen.getByRole("button", { name: "Load more" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "HISTORY_UNAVAILABLE",
  );
  expect(screen.getByText("local:first-page")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  await screen.findByText("local:second-page");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

it("submits an asynchronous scan job and polls until the canonical result is ready", async () => {
  const jobId = "22222222-2222-2222-2222-222222222222";
  let polls = 0;
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    const auth = authResult(url);
    if (auth) return { ok: true, status: 200, json: async () => auth };
    if (url.endsWith("/scans") && options?.method === "POST") {
      return {
        ok: true,
        status: 202,
        json: async () => ({ job_id: jobId, status: "QUEUED" }),
      };
    }
    if (url.endsWith(`/scan-jobs/${jobId}`)) {
      polls += 1;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          job_id: jobId,
          status: polls === 1 ? "RUNNING" : "COMPLETED",
        }),
      };
    }
    if (url.endsWith(`/scan-jobs/${jobId}/result`)) {
      return { ok: true, status: 200, json: async () => sourceResult };
    }
    return {
      ok: true,
      status: 200,
      json: async () => fixtures["authorization-removal"],
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

  await screen.findByText("Repository analysis", {}, { timeout: 4000 });
  expect(polls).toBe(2);
  expect(fetcher).toHaveBeenCalledWith(
    expect.stringContaining(`/scan-jobs/${jobId}/result`),
    expect.objectContaining({ credentials: "include" }),
  );
});

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

it("uses the backend capability contract in GitHub-optional local mode", async () => {
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => ({
    ok: true,
    json: async () =>
      url.endsWith("/auth/session")
        ? {
            authenticated: true,
            user: {
              username: "analyst",
              name: "Local analyst",
              role: "ANALYST",
            },
            github: { status: "NOT_CONFIGURED", permissions: [] },
            github_connection_required: false,
          }
        : url.endsWith("/auth/csrf")
          ? { headerName: "X-CSRF-TOKEN", token: "test-csrf" }
          : options?.method === "POST"
            ? sourceResult
            : fixtures["authorization-removal"],
  }));
  vi.stubGlobal("fetch", fetcher);

  render(<App />);
  await openView("New analysis");
  fillSource();
  const run = screen.getByRole("button", { name: "Run Analysis" });
  await waitFor(() => expect(run).toBeEnabled());
  fireEvent.click(run);
  await screen.findByText("Repository analysis");

  await openView("Open scan by ID");
  expect(screen.getByLabelText("Saved scan ID")).toBeInTheDocument();
  expect(
    screen.queryByText("Connect your GitHub account to access this page"),
  ).not.toBeInTheDocument();
  expect(
    fetcher.mock.calls.some(
      ([url, options]) => url.endsWith("/scans") && options?.method === "POST",
    ),
  ).toBe(true);
});

it("keeps local-mode Developers read-only without requiring GitHub", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        url.endsWith("/auth/session")
          ? {
              authenticated: true,
              user: {
                username: "developer",
                name: "Local developer",
                role: "DEVELOPER",
              },
              github: { status: "NOT_CONFIGURED", permissions: [] },
              github_connection_required: false,
            }
          : fixtures["authorization-removal"],
    })),
  );

  render(<App />);
  await openView("New analysis");
  expect(screen.getByLabelText("Repository path")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Run Analysis" })).toBeDisabled();
  await openView("Open scan by ID");
  expect(screen.getByLabelText("Saved scan ID")).toBeInTheDocument();
});

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
    repository: "/allowlisted/repo",
    old_commit: "a".repeat(40),
    new_commit: "b".repeat(40),
  });
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "Refresh analysis" }),
    ).toBeEnabled(),
  );
  vi.stubGlobal("confirm", vi.fn(() => true));
  fireEvent.change(screen.getByLabelText("Demo scenario"), {
    target: { value: "authorization-removal" },
  });
  await screen.findByText("Spring Boot demo");
});

it("keeps the current repository result when switching to a demo scenario is declined", async () => {
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => ({
    ok: true,
    json: async () =>
      authResult(url) ??
      (options?.method === "POST" ? sourceResult : fixtures["safe-change"]),
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
  const demoRequestsBefore = fetcher.mock.calls.filter(([url]) =>
    url.includes("/demo/scenarios/"),
  ).length;
  vi.stubGlobal("confirm", vi.fn(() => false));
  fireEvent.change(screen.getByLabelText("Demo scenario"), {
    target: { value: "safe-change" },
  });
  expect(screen.getByText("Repository analysis")).toBeInTheDocument();
  expect(screen.queryByText("Spring Boot demo")).not.toBeInTheDocument();
  expect(
    fetcher.mock.calls.filter(([url]) => url.includes("/demo/scenarios/"))
      .length,
  ).toBe(demoRequestsBefore);
});

it("marks retained evidence stale when a source refresh fails", async () => {
  let analysisRequests = 0;
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    const auth = authResult(url);
    if (auth) return { ok: true, json: async () => auth };
    if (url.endsWith("/scans") && options?.method === "POST") {
      analysisRequests += 1;
      if (analysisRequests === 1) {
        return { ok: true, json: async () => sourceResult };
      }
      return {
        ok: false,
        json: async () => ({
          code: "DEPENDENCY_UNAVAILABLE",
          message: "Analyzer unavailable",
        }),
      };
    }
    return { ok: true, json: async () => fixtures["authorization-removal"] };
  });
  vi.stubGlobal("fetch", fetcher);

  render(<App />);
  await openView("New analysis");
  fillSource();
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Run Analysis" })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole("button", { name: "Run Analysis" }));
  await screen.findByText("Repository analysis");
  fireEvent.click(screen.getByRole("button", { name: "Refresh analysis" }));

  const stale = (await screen.findByText("STALE RESULT")).closest(
    '[role="alert"]',
  );
  expect(stale).not.toBeNull();
  expect(stale).toHaveTextContent("STALE RESULT");
  expect(stale).toHaveTextContent("local:test");
  expect(stale).toHaveTextContent("aaaaaaa → bbbbbbb");
  expect(screen.getByText("Repository analysis")).toBeInTheDocument();
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
  await openView("Open scan by ID");
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
    screen.getByRole("button", { name: "Validate supported Docker finding" }),
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
  await openView("Open scan by ID");
  fireEvent.change(screen.getByLabelText("Saved scan ID"), {
    target: { value: sourceResult.scan_id },
  });
  fireEvent.click(screen.getByRole("button", { name: "Open scan" }));
  const validate = await screen.findByRole("button", {
    name: "Validate supported Docker finding",
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
    if (url.endsWith("/scans") && options?.method === "POST") return pending;
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
    expect(fetcher.mock.calls.some(([url]) => url.endsWith("/scans"))).toBe(
      true,
    ),
  );
  await openView("Account");
  fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
  await screen.findByRole("button", { name: "Sign in" });
  const call = fetcher.mock.calls.find(([url]) => url.endsWith("/scans"));
  expect(call![1]!.signal!.aborted).toBe(true);
  finish({ ok: true, json: async () => sourceResult });
  await openView("New analysis");
  await screen.findByText("Sign in to access this page");
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
  await openView("Open scan by ID");
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
