import { apiGet, apiPostForm, apiPostText } from "./client";
import type {
  SchemaListResponse,
  SchemaResponse,
  StatsResponse,
  QueryLogsResponse,
  IngestionLogsResponse,
  IngestionResponse,
  EngineId,
} from "./types";

/**
 * Typed accessors for every backend resource the UI needs. One method per
 * surface endpoint. To add a new backend call: add a method here and a
 * matching Route Handler under src/app/api. Streaming queries are handled by
 * the use-query-stream hook, not here.
 */
export const api = {
  listSchemas: () => apiGet<SchemaListResponse>("/api/schemas"),

  registerSchema: (yaml: string) =>
    apiPostText<SchemaResponse>("/api/schemas", yaml, "application/x-yaml"),

  getSchema: (schemaId: string) =>
    apiGet<SchemaResponse>(`/api/schemas/${encodeURIComponent(schemaId)}`),

  getStats: (schemaId: string) =>
    apiGet<StatsResponse>(`/api/schemas/${encodeURIComponent(schemaId)}/stats`),

  getQueryLogs: (schemaId: string, limit = 50) =>
    apiGet<QueryLogsResponse>(
      `/api/schemas/${encodeURIComponent(schemaId)}/logs/query?limit=${limit}`,
    ),

  getIngestionLogs: (schemaId: string, limit = 50) =>
    apiGet<IngestionLogsResponse>(
      `/api/schemas/${encodeURIComponent(schemaId)}/logs/ingestion?limit=${limit}`,
    ),

  ingestFile: (schemaId: string, file: File, engine: EngineId) => {
    const form = new FormData();
    form.set("file", file, file.name);
    // Surface resolves the engine from the file part's Content-Type, so we tell
    // the proxy which engine media type to stamp on the forwarded part.
    return apiPostForm<IngestionResponse>(
      `/api/schemas/${encodeURIComponent(schemaId)}/ingest?engine=${engine}`,
      form,
    );
  },

  getHealth: () =>
    apiGet<{
      status: string;
      web: { status: string };
      surface: { status: string; detail?: string };
    }>("/api/health"),
};
