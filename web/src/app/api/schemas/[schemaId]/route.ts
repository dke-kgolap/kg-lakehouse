import { proxyJson } from "@/app/api/_proxy/surface";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ schemaId: string }> };

/** GET /api/schemas/{schemaId} → schema dimensions/levels. */
export async function GET(req: Request, ctx: Ctx) {
  const { schemaId } = await ctx.params;
  return proxyJson(`/api/schemas/${encodeURIComponent(schemaId)}`, {
    auth: req.headers.get("authorization"),
  });
}
