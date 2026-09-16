import { expect, test, type Page, type Route } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const fixtures = JSON.parse(
  readFileSync(
    fileURLToPath(new URL("../src/fixtures.json", import.meta.url)),
    "utf8",
  ),
) as Record<string, Record<string, unknown>>;

type RouteState = {
  authenticated: boolean;
  role?: "DEVELOPER" | "ANALYST" | "ADMIN";
  connected?: boolean;
  analysisFailure?: boolean;
  validationFailure?: boolean;
};

const sourceResult = {
  ...fixtures["authorization-removal"],
  scan_id: "d".repeat(64),
  scenario: "local-source",
  provenance: {
    repository_path: "/allowlisted/repo",
    repository_identity: "local:browser-test",
    old_commit: "a".repeat(40),
    new_commit: "b".repeat(40),
  },
};

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

async function mockPlatform(page: Page, state: RouteState) {
  await page.route("http://localhost:8080/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === "/auth/session") {
      return json(
        route,
        state.authenticated
          ? {
              authenticated: true,
              user: {
                username: "tester",
                name: "Browser tester",
                role: state.role ?? "ADMIN",
              },
              github:
                state.connected === false
                  ? { status: "DISCONNECTED" }
                  : {
                      status: "CONNECTED",
                      login: "octocat",
                      permissions: ["Identity verified"],
                    },
              github_connection_required: true,
            }
          : {
              authenticated: false,
              github: { status: "DISCONNECTED" },
              github_connection_required: false,
            },
      );
    }
    if (url.pathname === "/auth/csrf") {
      return json(route, { headerName: "X-CSRF-TOKEN", token: "browser-csrf" });
    }
    if (url.pathname === "/auth/login") {
      state.authenticated = true;
      state.connected = true;
      return json(route, { authenticated: true });
    }
    if (url.pathname === "/auth/logout") {
      state.authenticated = false;
      return json(route, { authenticated: false });
    }
    if (url.pathname.startsWith("/demo/scenarios/")) {
      const scenario = url.pathname.split("/").at(-1)!;
      return json(route, fixtures[scenario]);
    }
    if (url.pathname === "/scans" && request.method() === "GET") {
      return json(route, { items: [], next_cursor: null });
    }
    if (url.pathname === "/scans" && request.method() === "POST") {
      return state.analysisFailure
        ? json(
            route,
            { code: "DEPENDENCY_UNAVAILABLE", message: "Analyzer unavailable" },
            503,
          )
        : json(route, sourceResult);
    }
    if (url.pathname.endsWith("/validation")) {
      return state.validationFailure
        ? json(
            route,
            {
              code: "VALIDATION_BUSY",
              message: "Validation capacity exhausted",
            },
            503,
          )
        : json(route, sourceResult);
    }
    if (url.pathname.startsWith("/analyses/")) {
      return json(
        route,
        { code: "ACCESS_DENIED", message: "Scan access denied" },
        403,
      );
    }
    return json(route, { code: "NOT_FOUND" }, 404);
  });
}

test("login, GitHub connection, logout, and serious accessibility", async ({
  page,
}) => {
  const state: RouteState = { authenticated: false };
  await mockPlatform(page, state);
  await page.goto("/#account");
  await page.getByLabel("Username").fill("tester");
  await page.getByLabel("Password").fill("test-password-only");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Signed in as")).toContainText("Browser tester");
  await expect(page.getByText("Connected as")).toContainText("@octocat");

  const accessibility = await new AxeBuilder({ page }).analyze();
  expect(
    accessibility.violations.filter(
      (item) => item.impact === "critical" || item.impact === "serious",
    ),
  ).toEqual([]);

  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
});

test("degraded analysis keeps keyboard-accessible graph evidence", async ({
  page,
}) => {
  await mockPlatform(page, {
    authenticated: true,
    role: "ANALYST",
    connected: true,
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Decision: BLOCK" }),
  ).toBeVisible();
  const directory = page.getByText(/Inspect all \d+ nodes/).first();
  await directory.focus();
  await directory.press("Enter");
  await expect(
    page.getByRole("navigation", { name: /node pages/i }).first(),
  ).toBeVisible();
});

test("failed validation preserves and labels the stale source result", async ({
  page,
}) => {
  await mockPlatform(page, {
    authenticated: true,
    role: "ANALYST",
    connected: true,
    validationFailure: true,
  });
  await page.goto("/#new-analysis");
  await page.getByLabel("Repository path").fill("/allowlisted/repo");
  await page.getByLabel("Old commit SHA").fill("a".repeat(40));
  await page.getByLabel("New commit SHA").fill("b".repeat(40));
  await page.getByRole("button", { name: "Run Analysis" }).click();
  await page.waitForURL(/#analysis$/);
  await expect(page.getByText("Repository analysis")).toBeVisible();
  const validate = page.getByRole("button", {
    name: "Validate supported Docker finding",
  });
  await expect(validate).toBeEnabled();
  await validate.click();
  await expect(page.getByText("STALE RESULT")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Decision: BLOCK" }),
  ).toBeVisible();
});

test("scan access denial is explicit and keeps the prior result", async ({
  page,
}) => {
  await mockPlatform(page, {
    authenticated: true,
    role: "DEVELOPER",
    connected: true,
  });
  await page.goto("/#saved-scans");
  await page.getByLabel("Saved scan ID").fill("e".repeat(64));
  await page.getByRole("button", { name: "Open scan" }).click();
  await expect(page.getByRole("alert")).toContainText("ACCESS_DENIED");
});
