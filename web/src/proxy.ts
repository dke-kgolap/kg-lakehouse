import { NextResponse, type NextRequest } from "next/server";

/**
 * Auth gate. The surface BFF uses HTTP Basic auth; rather than store fixed
 * credentials, we pass the user's credentials through:
 *
 *  - Any request without a Basic `Authorization` header gets a 401 challenge,
 *    which makes the browser show its native login dialog.
 *  - On a full page load we validate the entered credentials against surface
 *    (a wrong password re-prompts). API requests carry the same cached
 *    credentials and are validated by surface itself when the proxy forwards them.
 *
 * `/api/health` and static assets are excluded so the container healthcheck and
 * asset loading keep working without credentials.
 */
const REALM = "KG-OLAP Console";

function challenge(): NextResponse {
  return new NextResponse("Authentication required", {
    status: 401,
    headers: {
      "WWW-Authenticate": `Basic realm="${REALM}", charset="UTF-8"`,
      "Cache-Control": "no-store",
    },
  });
}

export async function proxy(request: NextRequest): Promise<NextResponse> {
  const auth = request.headers.get("authorization");
  if (!auth || !auth.toLowerCase().startsWith("basic ")) {
    return challenge();
  }

  // Validate real credentials against surface on top-level navigations only,
  // so a wrong password can't load the app. Soft navigations / API calls reuse
  // the browser-cached credentials and are validated downstream by surface.
  if (request.headers.get("sec-fetch-dest") === "document") {
    const base = (
      process.env.SURFACE_BASE_URL ?? "http://localhost:8080"
    ).replace(/\/$/, "");
    try {
      const res = await fetch(`${base}/api/schemas`, {
        headers: { authorization: auth },
        cache: "no-store",
      });
      if (res.status === 401 || res.status === 403) return challenge();
    } catch {
      // Surface unreachable — let the app load and surface its own error state.
    }
  }

  return NextResponse.next();
}

export const config = {
  // Exclude all Next.js internals (_next/*), static assets, and the open
  // health endpoint. Only protect actual app routes and /api/* routes.
  matcher: ["/((?!_next/|favicon.ico|api/health).*)"],
};
