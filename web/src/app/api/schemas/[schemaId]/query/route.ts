import { proxyStream } from "@/app/api/_proxy/surface";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ schemaId: string }> };

/**
 * POST /api/schemas/{schemaId}/query
 * Text DSL query. Body is text/plain; `representation` and `reasoning` are
 * query params. Response is streamed NDJSON.
 */
export async function POST(req: Request, ctx: Ctx) {
  const { schemaId } = await ctx.params;
  const incoming = new URL(req.url).searchParams;
  const search = new URLSearchParams();
  search.set("representation", incoming.get("representation") ?? "RDF");
  search.set("reasoning", incoming.get("reasoning") ?? "false");

  const body = await req.text();
  return proxyStream(`/api/schemas/${encodeURIComponent(schemaId)}/query`, {
    method: "POST",
    body,
    headers: { "content-type": "text/plain" },
    search,
    auth: req.headers.get("authorization"),
  });
}
