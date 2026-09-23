import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { useState } from "react";
import {
  AccountPanel,
  GitHubConnectionCard,
  type Account,
} from "./AccountPanel";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
const analyst = { username: "analyst", name: "Test analyst", role: "ANALYST" };
const admin = { username: "admin", name: "Test admin", role: "ADMIN" };

function AccountHarness({
  initialUser = null,
  onChange,
}: {
  initialUser?: Account | null;
  onChange: (user: Account | null) => void;
}) {
  const [user, setUser] = useState<Account | null>(initialUser);
  return (
    <AccountPanel
      user={user}
      onUserChange={(next) => {
        setUser(next);
        onChange(next);
      }}
    />
  );
}

it("signs in with session cookies and CSRF, clears the password, and signs out", async () => {
  let authenticated = false;
  const fetcher = vi.fn(async (url: string, _options?: RequestInit) => {
    if (url.endsWith("/auth/login")) authenticated = true;
    if (url.endsWith("/auth/logout")) authenticated = false;
    return {
      ok: true,
      json: async () =>
        url.endsWith("/auth/csrf")
          ? { headerName: "X-CSRF-TOKEN", token: "test-token" }
          : { authenticated, user: authenticated ? analyst : null },
    };
  });
  vi.stubGlobal("fetch", fetcher);
  const change = vi.fn();
  render(<AccountHarness onChange={change} />);
  fireEvent.change(screen.getByLabelText("Username"), {
    target: { value: "analyst" },
  });
  fireEvent.change(screen.getByLabelText("Password"), {
    target: { value: "test-password-only" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
  await screen.findByText("Test analyst");
  expect(change).toHaveBeenCalledWith(analyst);
  const login = fetcher.mock.calls.find(([url]) => url.endsWith("/auth/login"));
  expect(login![1]!.credentials).toBe("include");
  expect(new Headers(login![1]!.headers).get("X-CSRF-TOKEN")).toBe(
    "test-token",
  );
  expect((login![1]!.body as URLSearchParams).get("username")).toBe("analyst");
  fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
  await screen.findByRole("button", { name: "Sign in" });
  expect(screen.getByLabelText("Password")).toHaveValue("");
  expect(change).toHaveBeenLastCalledWith(null);
});

it("retains Admin account creation and updates the account list", async () => {
  let created = false;
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/admin/users") && options?.method === "POST")
      created = true;
    return {
      ok: true,
      json: async () =>
        url.endsWith("/auth/csrf")
          ? { headerName: "X-CSRF-TOKEN", token: "test-token" }
          : url.endsWith("/admin/users")
            ? created
              ? [admin, analyst]
              : [admin]
            : { authenticated: true, user: admin },
    };
  });
  vi.stubGlobal("fetch", fetcher);
  render(<AccountHarness initialUser={admin as Account} onChange={vi.fn()} />);
  const summary = await screen.findByText("Manage accounts");
  fireEvent.click(summary);
  fireEvent.change(screen.getByLabelText("New username"), {
    target: { value: "analyst" },
  });
  fireEvent.change(screen.getByLabelText("Display name"), {
    target: { value: "Test analyst" },
  });
  fireEvent.change(screen.getByLabelText("Platform role"), {
    target: { value: "ANALYST" },
  });
  fireEvent.change(screen.getByLabelText("New password"), {
    target: { value: "test-password-only" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Create account" }));
  await screen.findByText("Account created.");
  expect(
    await screen.findByText(/Test analyst.*analyst.*ANALYST/),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("New password")).toHaveValue("");
  const create = fetcher.mock.calls.find(
    ([url, options]) =>
      url.endsWith("/admin/users") && options?.method === "POST",
  );
  expect(JSON.parse(create![1]!.body as string)).toEqual({
    username: "analyst",
    name: "Test analyst",
    role: "ANALYST",
    password: "test-password-only",
  });

  fireEvent.change(screen.getByLabelText("Account username"), {
    target: { value: "analyst" },
  });
  fireEvent.change(screen.getByLabelText("Notification email"), {
    target: { value: "analyst@example.com" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Save notification email" }),
  );
  await screen.findByText("Notification email updated.");
  const emailUpdate = fetcher.mock.calls.find(([url]) =>
    url.endsWith("/admin/users/analyst/email"),
  );
  expect(JSON.parse(emailUpdate![1]!.body as string)).toEqual({
    email: "analyst@example.com",
    verified: true,
  });
});

it("describes missing installation credentials without claiming OAuth is unimplemented", () => {
  render(
    <GitHubConnectionCard
      connection={{ status: "NOT_CONFIGURED" }}
      connectUrl="/auth/github/connect"
    />,
  );

  expect(
    screen.getByText(/installation has not configured GitHub sign-in/i),
  ).toBeInTheDocument();
  expect(
    screen.queryByText(/sign-in is not enabled in the platform API yet/i),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Connect GitHub" })).toBeDisabled();
});
