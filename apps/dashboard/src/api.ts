export const platformUrl =
  import.meta.env.VITE_PLATFORM_API_BASE_URL ?? "http://localhost:8080";

type CsrfToken = { headerName: string; token: string };
let csrfToken: CsrfToken | null = null;
let csrfRequest: Promise<CsrfToken> | null = null;

export function clearCsrfToken() {
  csrfToken = null;
  csrfRequest = null;
}

async function getCsrfToken(): Promise<CsrfToken> {
  if (csrfToken) return csrfToken;
  if (!csrfRequest) {
    csrfRequest = fetch(`${platformUrl}/auth/csrf`, {
      credentials: "include",
    })
      .then(async (response) => {
        if (!response.ok)
          throw new Error("Could not establish a secure session");
        const value = (await response.json()) as CsrfToken;
        csrfToken = value;
        return value;
      })
      .finally(() => {
        csrfRequest = null;
      });
  }
  return csrfRequest;
}

async function send(
  route: string,
  options: RequestInit,
  csrf: CsrfToken | null,
): Promise<Response> {
  const headers = new Headers(options.headers);
  if (csrf) headers.set(csrf.headerName, csrf.token);
  return fetch(platformUrl + route, {
    ...options,
    headers,
    credentials: "include",
  });
}

async function isCsrfRejection(response: Response): Promise<boolean> {
  if (response.status !== 403) return false;
  try {
    const error = (await response.clone().json()) as { code?: string };
    return error.code === "INVALID_CSRF_TOKEN";
  } catch {
    return false;
  }
}

export async function api(
  route: string,
  options: RequestInit = {},
): Promise<Response> {
  const mutation = Boolean(options.method && options.method !== "GET");
  let response = await send(
    route,
    options,
    mutation ? await getCsrfToken() : null,
  );
  if (mutation && (await isCsrfRejection(response))) {
    clearCsrfToken();
    response = await send(route, options, await getCsrfToken());
  }
  if (response.status === 401) {
    clearCsrfToken();
    window.dispatchEvent(new Event("riskgraph-session-expired"));
  }
  return response;
}
