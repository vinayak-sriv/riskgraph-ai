import { expect, test, type Page, type Route } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const fixtures = JSON.parse(
  readFileSync(
    fileURLToPath(new URL("../src/fixtures.json", import.meta.url)),
    "utf8",
  ),
) as Record<string, Record<string, unknown>>;

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

async function mockDemoPlatform(page: Page) {
  await page.route("http://localhost:8080/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === "/auth/session") {
      return json(route, {
        authenticated: true,
        user: {
          username: "demo-analyst",
          name: "Security analyst",
          role: "ANALYST",
        },
        github: {
          status: "CONNECTED",
          login: "riskgraph-demo",
          permissions: ["Identity verified"],
        },
        github_connection_required: true,
      });
    }
    if (url.pathname === "/auth/csrf") {
      return json(route, { headerName: "X-CSRF-TOKEN", token: "demo-csrf" });
    }
    if (url.pathname.startsWith("/demo/scenarios/")) {
      const scenario = url.pathname.split("/").at(-1)!;
      return json(route, fixtures[scenario]);
    }
    if (url.pathname === "/scans" && route.request().method() === "GET") {
      return json(route, { items: [], next_cursor: null });
    }
    return json(route, { code: "NOT_FOUND" }, 404);
  });
}

test("record the deterministic product walkthrough", async ({ page }) => {
  await mockDemoPlatform(page);
  await page.goto("/");

  await expect(
    page.getByRole("heading", { name: "Decision: BLOCK" }),
  ).toBeVisible();
  await page.waitForTimeout(1_200);

  await page.getByRole("button", { name: "After", exact: true }).click();
  await page.waitForTimeout(1_000);
  await page.getByRole("button", { name: "Compare", exact: true }).click();
  await page.waitForTimeout(1_000);

  await page.getByLabel("Demo scenario").selectOption("safe-change");
  await expect(
    page.getByRole("heading", { name: "Decision: ALLOW" }),
  ).toBeVisible();
  await page.waitForTimeout(1_200);

  await page
    .getByLabel("Demo scenario")
    .selectOption("new-public-sensitive-endpoint");
  await expect(
    page.getByRole("heading", { name: "Decision: BLOCK" }),
  ).toBeVisible();
  await page.waitForTimeout(1_200);

  await page.getByLabel("Demo scenario").selectOption("authorization-removal");
  await expect(page.getByText("Risk delta").first()).toBeVisible();
  await page.waitForTimeout(1_500);
});
