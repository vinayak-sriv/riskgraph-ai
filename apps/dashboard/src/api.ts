export const platformUrl =
  import.meta.env.VITE_PLATFORM_API_BASE_URL ?? "http://localhost:8080";

export async function api(
  route: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(options.headers);
  if (options.method && options.method !== "GET") {
    const response = await fetch(`${platformUrl}/auth/csrf`, {
      credentials: "include",
    });
    if (!response.ok) throw new Error("Could not establish a secure session");
    const csrf = await response.json();
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(platformUrl + route, {
    ...options,
    headers,
    credentials: "include",
  });
  if (response.status === 401)
    window.dispatchEvent(new Event("riskgraph-session-expired"));
  return response;
}
