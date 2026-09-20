import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import {
  NotificationCenter,
  type NotificationItem,
} from "./NotificationCenter";
import type { Account } from "./AccountPanel";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const analyst: Account = {
  username: "analyst",
  name: "Test analyst",
  role: "ANALYST",
};

const item1: NotificationItem = {
  id: 1,
  repository: "org/repo-a",
  verdict: "BLOCK",
  risk_before: 40,
  risk_after: 90,
  scan_id: "scan-1",
  created_at: "2026-09-01T00:00:00Z",
  read_at: null,
};
const item2: NotificationItem = {
  id: 2,
  repository: null,
  verdict: "REVIEW",
  risk_before: null,
  risk_after: null,
  scan_id: "scan-2",
  created_at: "2026-09-02T00:00:00Z",
  read_at: "2026-09-02T01:00:00Z",
};
const item3: NotificationItem = {
  id: 3,
  repository: "org/repo-c",
  verdict: "REVIEW",
  risk_before: 10,
  risk_after: 20,
  scan_id: "scan-3",
  created_at: "2026-09-03T00:00:00Z",
  read_at: null,
};

const basePreferences = {
  channels: [
    { channel: "IN_APP", enabled: true, min_severity: "REVIEW" },
    { channel: "EMAIL", enabled: false, min_severity: "REVIEW" },
  ],
  unsubscribed_all: false,
};

it("renders nothing when signed out", () => {
  const { container } = render(<NotificationCenter user={null} />);
  expect(container).toBeEmptyDOMElement();
});

it("loads notifications, paginates, marks one and all read, and manages preferences", async () => {
  let inAppEnabled = true;
  let unsubscribedAll = false;
  const fetcher = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/auth/csrf"))
      return {
        ok: true,
        json: async () => ({ headerName: "X-CSRF-TOKEN", token: "test-token" }),
      };
    if (url.endsWith("/notifications?cursor=2"))
      return {
        ok: true,
        json: async () => ({
          items: [item3],
          next_cursor: null,
          unread_count: 2,
        }),
      };
    if (url.endsWith("/notifications"))
      return {
        ok: true,
        json: async () => ({
          items: [item1, item2],
          next_cursor: 2,
          unread_count: 1,
        }),
      };
    if (url.endsWith("/notifications/1/read"))
      return { ok: true, json: async () => ({}) };
    if (url.endsWith("/notifications/read-all"))
      return { ok: true, json: async () => ({}) };
    if (
      url.endsWith("/notifications/preferences") &&
      options?.method === "PUT"
    ) {
      const body = JSON.parse(options.body as string) as {
        channel: string;
        enabled: boolean;
        min_severity: string;
      };
      if (body.channel === "IN_APP") inAppEnabled = body.enabled;
      return {
        ok: true,
        json: async () => ({
          channels: [
            {
              channel: "IN_APP",
              enabled: inAppEnabled,
              min_severity: body.min_severity,
            },
            { channel: "EMAIL", enabled: false, min_severity: "REVIEW" },
          ],
          unsubscribed_all: unsubscribedAll,
        }),
      };
    }
    if (url.endsWith("/notifications/preferences"))
      return { ok: true, json: async () => basePreferences };
    if (url.endsWith("/notifications/preferences/unsubscribe-all")) {
      const body = JSON.parse((options?.body as string) ?? "{}") as {
        unsubscribed: boolean;
      };
      unsubscribedAll = body.unsubscribed;
      return {
        ok: true,
        json: async () => ({
          channels: basePreferences.channels,
          unsubscribed_all: unsubscribedAll,
        }),
      };
    }
    throw new Error(`unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", fetcher);

  render(<NotificationCenter user={analyst} />);

  expect(await screen.findByText("org/repo-a")).toBeInTheDocument();
  expect(screen.getByText("Risk 40 → 90")).toBeInTheDocument();
  expect(screen.getByText("Unknown repository")).toBeInTheDocument();
  expect(screen.getByText("1")).toBeInTheDocument();

  fireEvent.click(screen.getByText("Load more"));
  await screen.findByText("org/repo-c");
  expect(screen.getByText("2")).toBeInTheDocument();

  fireEvent.click(screen.getAllByRole("button", { name: /Mark read/ })[0]);
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(([url]) =>
        (url as string).endsWith("/notifications/1/read"),
      ),
    ).toBe(true),
  );
  await waitFor(() => expect(screen.getByText("1")).toBeInTheDocument());

  fireEvent.click(screen.getByRole("button", { name: /Mark all read/ }));
  await waitFor(() =>
    expect(
      screen.queryByRole("button", { name: /Mark all read/ }),
    ).not.toBeInTheDocument(),
  );
  expect(screen.queryAllByRole("button", { name: /Mark read/ })).toHaveLength(
    0,
  );

  fireEvent.click(await screen.findByText("Manage notification preferences"));
  const inAppCheckbox = screen.getByRole("checkbox", { name: /In-app/ });
  expect(inAppCheckbox).toBeChecked();
  const inAppRow = inAppCheckbox.closest(
    ".notification-preference-row",
  ) as HTMLElement;
  const severitySelect = within(inAppRow).getByRole("combobox");
  fireEvent.change(severitySelect, { target: { value: "BLOCK" } });
  await waitFor(() =>
    expect(
      within(inAppRow).getByDisplayValue("Block only"),
    ).toBeInTheDocument(),
  );

  fireEvent.click(inAppCheckbox);
  await waitFor(() => expect(inAppCheckbox).not.toBeChecked());

  const unsubscribeAll = screen.getByRole("checkbox", {
    name: /Unsubscribe from all/,
  });
  expect(unsubscribeAll).not.toBeChecked();
  fireEvent.click(unsubscribeAll);
  await waitFor(() => expect(unsubscribeAll).toBeChecked());
});

it("shows an error state when the feed fails to load, without crashing on missing preferences", async () => {
  const fetcher = vi.fn(async (url: string) => {
    if (url.endsWith("/notifications"))
      return { ok: false, json: async () => ({}) };
    if (url.endsWith("/notifications/preferences"))
      throw new Error("network down");
    throw new Error(`unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", fetcher);

  render(<NotificationCenter user={analyst} />);

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Notifications unavailable",
  );
  expect(
    screen.queryByText("Manage notification preferences"),
  ).not.toBeInTheDocument();
});

it("shows an empty state when there are no notifications", async () => {
  const fetcher = vi.fn(async (url: string) => {
    if (url.endsWith("/notifications"))
      return {
        ok: true,
        json: async () => ({ items: [], next_cursor: null, unread_count: 0 }),
      };
    if (url.endsWith("/notifications/preferences"))
      return { ok: false, json: async () => ({}) };
    throw new Error(`unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", fetcher);

  render(<NotificationCenter user={analyst} />);

  expect(await screen.findByText("No notifications yet.")).toBeInTheDocument();
});
