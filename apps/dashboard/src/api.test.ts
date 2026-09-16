import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, clearCsrfToken } from "./api";

const csrf = (token: string) =>
  new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
const invalidCsrf = () =>
  new Response(JSON.stringify({ code: "INVALID_CSRF_TOKEN" }), {
    status: 403,
    headers: { "Content-Type": "application/json" },
  });

describe("api CSRF lifecycle", () => {
  beforeEach(() => {
    clearCsrfToken();
    vi.restoreAllMocks();
  });

  it("reuses one token across mutations in the same session", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrf("one"))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }));

    await api("/analyses", { method: "POST", body: "first" });
    await api("/analyses", { method: "POST", body: "second" });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(
      fetchMock.mock.calls.filter(([url]) =>
        String(url).endsWith("/auth/csrf"),
      ),
    ).toHaveLength(1);
    const firstHeaders = fetchMock.mock.calls[1][1]?.headers as Headers;
    const secondHeaders = fetchMock.mock.calls[2][1]?.headers as Headers;
    expect(firstHeaders.get("X-CSRF-TOKEN")).toBe("one");
    expect(secondHeaders.get("X-CSRF-TOKEN")).toBe("one");
  });

  it("refreshes a rejected token and retries the mutation exactly once", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrf("expired"))
      .mockResolvedValueOnce(invalidCsrf())
      .mockResolvedValueOnce(csrf("fresh"))
      .mockResolvedValueOnce(new Response(null, { status: 200 }));

    const response = await api("/analyses", { method: "POST", body: "safe" });

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(4);
    const retriedHeaders = fetchMock.mock.calls[3][1]?.headers as Headers;
    expect(retriedHeaders.get("X-CSRF-TOKEN")).toBe("fresh");
  });

  it("does not loop when the one retry is also forbidden", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrf("expired"))
      .mockResolvedValueOnce(invalidCsrf())
      .mockResolvedValueOnce(csrf("fresh"))
      .mockResolvedValueOnce(invalidCsrf());

    const response = await api("/analyses", { method: "POST" });

    expect(response.status).toBe(403);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("does not retry a permission denial", async () => {
    const forbidden = new Response(JSON.stringify({ code: "ACCESS_DENIED" }), {
      status: 403,
      headers: { "Content-Type": "application/json" },
    });
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrf("valid"))
      .mockResolvedValueOnce(forbidden);

    const response = await api("/admin/users", { method: "POST" });

    expect(response.status).toBe(403);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
