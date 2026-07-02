import { proxyJson } from "@/app/api/_proxy/surface";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** GET /api/schemas → list registered schema ids. */
export async function GET(req: Request) {
  return proxyJson("/api/schemas", {
    auth: req.headers.get("authorization"),
  });
}

/** POST /api/schemas → register a schema from YAML (body forwarded verbatim). */
export async function POST(req: Request) {
  const body = await req.text();
  return proxyJson("/api/schemas", {
    method: "POST",
    body,
    headers: {
      "content-type": req.headers.get("content-type") ?? "application/x-yaml",
    },
    auth: req.headers.get("authorization"),
  });
}
