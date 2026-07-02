import { surfaceUrl } from "@/app/api/_proxy/surface";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * GET /api/health — app-level health. Always reports the web app as UP and
 * probes the surface BFF (its /api/health is unauthenticated). This route is
 * excluded from the auth proxy so the container healthcheck works without
 * credentials.
 */
export async function GET() {
  let surface: { status: string; detail?: string } = { status: "UNKNOWN" };
  try {
    const res = await fetch(surfaceUrl("/api/health"), { cache: "no-store" });
    surface = res.ok
      ? { status: "UP" }
      : { status: "DOWN", detail: `HTTP ${res.status}` };
  } catch (err) {
    surface = {
      status: "DOWN",
      detail: err instanceof Error ? err.message : String(err),
    };
  }

  const ok = surface.status === "UP";
  return Response.json(
    { status: ok ? "UP" : "DEGRADED", web: { status: "UP" }, surface },
    { status: 200, headers: { "cache-control": "no-store" } },
  );
}
