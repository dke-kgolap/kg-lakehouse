import { proxyJson } from "@/app/api/_proxy/surface";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ schemaId: string }> };

/** GET /api/schemas/{schemaId}/logs/query?limit=N */
export async function GET(req: Request, ctx: Ctx) {
  const { schemaId } = await ctx.params;
  const search = new URLSearchParams();
  const limit = new URL(req.url).searchParams.get("limit");
  if (limit) search.set("limit", limit);
  return proxyJson(`/api/schemas/${encodeURIComponent(schemaId)}/logs/query`, {
    search,
    auth: req.headers.get("authorization"),
  });
}
