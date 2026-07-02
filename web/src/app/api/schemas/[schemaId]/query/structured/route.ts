import { proxyStream } from "@/app/api/_proxy/surface";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ schemaId: string }> };

/**
 * POST /api/schemas/{schemaId}/query/structured
 * Structured query (JSON body). Response is streamed NDJSON.
 */
export async function POST(req: Request, ctx: Ctx) {
  const { schemaId } = await ctx.params;
  const body = await req.text();
  return proxyStream(
    `/api/schemas/${encodeURIComponent(schemaId)}/query/structured`,
    {
      method: "POST",
      body,
      headers: { "content-type": "application/json" },
      auth: req.headers.get("authorization"),
    },
  );
}
