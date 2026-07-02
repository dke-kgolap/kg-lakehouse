import "server-only";
import { serverEnv } from "@/lib/env";

/**
 * Server-side proxy helpers. The browser only ever calls our own `/api/*`
 * routes; these helpers forward to the surface BFF. Credentials are NOT stored
 * here — we pass through the caller's `Authorization` header (HTTP Basic), so
 * the user's own surface credentials are used. This is the single seam where
 * the frontend microservice talks to the backend.
 */

export function surfaceUrl(path: string, search?: URLSearchParams): string {
  const { SURFACE_BASE_URL } = serverEnv();
  const base = SURFACE_BASE_URL.replace(/\/$/, "");
  const qs = search && [...search.keys()].length ? `?${search.toString()}` : "";
  return `${base}${path.startsWith("/") ? path : `/${path}`}${qs}`;
}

function withAuth(
  headers: HeadersInit | undefined,
  auth: string | null | undefined,
): HeadersInit {
  const h = new Headers(headers);
  if (auth) h.set("Authorization", auth);
  return h;
}

/** Forward a non-streaming request and relay the JSON/text response verbatim. */
export async function proxyJson(
  path: string,
  init?: RequestInit & { search?: URLSearchParams; auth?: string | null },
): Promise<Response> {
  const { search, auth, ...rest } = init ?? {};
  let upstream: Response;
  try {
    upstream = await fetch(surfaceUrl(path, search), {
      ...rest,
      headers: withAuth(rest.headers, auth),
      cache: "no-store",
    });
  } catch (err) {
    return Response.json(
      {
        error: "surface_unreachable",
        message: err instanceof Error ? err.message : String(err),
      },
      { status: 502 },
    );
  }

  const body = await upstream.text();
  const contentType =
    upstream.headers.get("content-type") ?? "application/json";
  return new Response(body, {
    status: upstream.status,
    headers: { "content-type": contentType, "cache-control": "no-store" },
  });
}

/**
 * Forward a request and stream the NDJSON response body through to the client
 * chunk-by-chunk, without buffering. Used for the two query endpoints.
 */
export async function proxyStream(
  path: string,
  init: RequestInit & { search?: URLSearchParams; auth?: string | null },
): Promise<Response> {
  const { search, auth, ...rest } = init;
  let upstream: Response;
  try {
    upstream = await fetch(surfaceUrl(path, search), {
      ...rest,
      headers: withAuth(rest.headers, auth),
      cache: "no-store",
      // Node fetch requires duplex when sending a body.
      ...({ duplex: "half" } as Record<string, unknown>),
    });
  } catch (err) {
    return Response.json(
      {
        error: "surface_unreachable",
        message: err instanceof Error ? err.message : String(err),
      },
      { status: 502 },
    );
  }

  // On error status, relay the (small) error body as-is.
  if (!upstream.ok || !upstream.body) {
    const text = await upstream.text();
    return new Response(text, {
      status: upstream.status,
      headers: {
        "content-type":
          upstream.headers.get("content-type") ?? "application/json",
        "cache-control": "no-store",
      },
    });
  }

  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      "content-type": "application/x-ndjson",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}
