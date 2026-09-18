import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "./api";
import fixtures from "./fixtures.json";
import { useAnalysis } from "./useAnalysis";

vi.mock("./api", () => ({ api: vi.fn(), platformUrl: "http://localhost" }));

const alice = { username: "alice", name: "Alice", role: "ADMIN" as const };
const bob = { username: "bob", name: "Bob", role: "DEVELOPER" as const };
const session = (user = alice) => ({
  authenticated: true,
  user,
  github_connection_required: false,
});
const response = (value: unknown) =>
  ({ ok: true, json: async () => value }) as Response;
function deferred() {
  let resolve!: (value: Response) => void;
  const promise = new Promise<Response>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockResolvedValue(response(session()));
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(response(fixtures["authorization-removal"])),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("discards delayed history after logout even if transport ignores cancellation", async () => {
  const { result } = renderHook(useAnalysis);
  await waitFor(() => expect(result.current.canRead).toBe(true));
  const pending = deferred();
  vi.mocked(api).mockReturnValueOnce(pending.promise);
  let request!: Promise<void>;
  act(() => {
    request = result.current.loadHistory();
  });
  const signal = vi.mocked(api).mock.calls.at(-1)?.[1]?.signal;
  act(() => result.current.handleUserChange(null));
  expect(signal?.aborted).toBe(true);
  await act(async () => {
    pending.resolve(
      response({
        items: [{ repository: "alice-private" }],
        next_cursor: "secret",
      }),
    );
    await request;
  });
  act(() => result.current.handleUserChange(bob));
  expect(result.current.history).toEqual([]);
  expect(result.current.historyCursor).toBeNull();
  expect(result.current.historyLoading).toBe(false);
});

it("clears protected analysis and history on a direct account switch", async () => {
  const { result } = renderHook(useAnalysis);
  await waitFor(() => expect(result.current.canRead).toBe(true));
  vi.mocked(api).mockResolvedValueOnce(
    response({
      ...fixtures["authorization-removal"],
      provenance: { repository_identity: "alice-private" },
    }),
  );
  await act(async () => {
    await result.current.openScan("alice-scan");
  });
  vi.mocked(api).mockResolvedValueOnce(
    response({ items: [{ repository: "alice-private" }] }),
  );
  await act(async () => {
    await result.current.loadHistory();
  });
  expect(result.current.analysis.provenance).toBeDefined();
  act(() => result.current.handleUserChange(bob));
  expect(result.current.analysis.provenance).toBeUndefined();
  expect(result.current.history).toEqual([]);
});

it("ignores older overlapping session responses and responses after explicit logout", async () => {
  const { result } = renderHook(useAnalysis);
  await waitFor(() => expect(result.current.canRead).toBe(true));
  const older = deferred();
  const newer = deferred();
  vi.mocked(api)
    .mockReturnValueOnce(older.promise)
    .mockReturnValueOnce(newer.promise);
  let first!: Promise<void>;
  let second!: Promise<void>;
  act(() => {
    first = result.current.refreshSession();
    second = result.current.refreshSession();
  });
  await act(async () => {
    newer.resolve(response({ ...session(), user: bob }));
    await second;
  });
  await act(async () => {
    older.resolve(response(session()));
    await first;
  });
  expect(result.current.user?.username).toBe("bob");
  const afterLogout = deferred();
  vi.mocked(api).mockReturnValueOnce(afterLogout.promise);
  act(() => {
    first = result.current.refreshSession();
  });
  act(() => result.current.handleUserChange(null));
  await act(async () => {
    afterLogout.resolve(response(session()));
    await first;
  });
  expect(result.current.user).toBeNull();
});
