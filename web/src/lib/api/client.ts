/**
 * Browser-side fetch wrapper. ALL calls target our own same-origin `/api/*`
 * routes — never the surface BFF directly. This is what structurally enforces
 * the microservice boundary on the client.
 */

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * A 401 on an API call means the browser hasn't sent valid Basic credentials
 * (e.g. a tab left open from before login). Reload the top-level document so
 * the auth proxy issues its challenge and the browser shows the login dialog.
 */
let reloading = false;
function handleUnauthorized() {
  if (typeof window !== "undefined" && !reloading) {
    reloading = true;
    window.location.reload();
  }
}

async function toError(res: Response): Promise<ApiError> {
  if (res.status === 401) handleUnauthorized();
  let message = `Request failed (HTTP ${res.status})`;
  try {
    const data = await res.json();
    message =
      (data as { message?: string; detail?: string }).message ??
      (data as { detail?: string }).detail ??
      message;
  } catch {
    /* non-JSON body */
  }
  return new ApiError(res.status, message);
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(path, { headers: { accept: "application/json" } });
  if (!res.ok) throw await toError(res);
  return res.json() as Promise<T>;
}

export async function apiPostForm<T>(
  path: string,
  form: FormData,
): Promise<T> {
  const res = await fetch(path, { method: "POST", body: form });
  if (!res.ok) throw await toError(res);
  return res.json() as Promise<T>;
}

export async function apiPostText<T>(
  path: string,
  body: string,
  contentType: string,
): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "content-type": contentType },
    body,
  });
  if (!res.ok) throw await toError(res);
  return res.json() as Promise<T>;
}
