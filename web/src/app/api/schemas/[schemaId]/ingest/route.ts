import { proxyJson } from "@/app/api/_proxy/surface";
import { ENGINE_MEDIA_TYPE, type EngineId } from "@/lib/api/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ schemaId: string }> };

/**
 * POST /api/schemas/{schemaId}/ingest?engine=aixm|iwxxm|fixm
 * Multipart upload (form field "file"). Surface resolves the ingestion engine
 * from the file part's Content-Type, so we re-wrap the file with the media
 * type matching the requested engine before forwarding.
 */
export async function POST(req: Request, ctx: Ctx) {
  const { schemaId } = await ctx.params;
  const engine = (new URL(req.url).searchParams.get("engine") ??
    "aixm") as EngineId;
  const mediaType = ENGINE_MEDIA_TYPE[engine] ?? ENGINE_MEDIA_TYPE.aixm;

  const incoming = await req.formData();
  const file = incoming.get("file");
  if (!(file instanceof File)) {
    return Response.json(
      { error: "missing_file", message: "Expected a 'file' form field." },
      { status: 400 },
    );
  }

  const form = new FormData();
  const typed = new File([await file.arrayBuffer()], file.name, {
    type: mediaType,
  });
  form.set("file", typed, file.name);

  return proxyJson(`/api/schemas/${encodeURIComponent(schemaId)}/ingest`, {
    method: "POST",
    body: form,
    auth: req.headers.get("authorization"),
  });
}
