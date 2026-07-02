/**
 * Types mirroring the `surface` BFF contract (verified against the Java DTOs).
 * Kept framework-free so both the server proxy and the browser client share them.
 */

export type GraphRepresentation = "RDF" | "LPG" | "GRAPH_FRAME";

/** Ingestion engines. The surface resolves these via the file's media type. */
export type EngineId = "aixm" | "iwxxm" | "fixm";

export const ENGINES: EngineId[] = ["aixm", "iwxxm", "fixm"];

/** Media type the surface expects per engine on the multipart file part. */
export const ENGINE_MEDIA_TYPE: Record<EngineId, string> = {
  aixm: "application/xml+aixm",
  iwxxm: "application/xml+iwxxm",
  fixm: "application/xml+fixm",
};

export const REPRESENTATIONS: GraphRepresentation[] = [
  "RDF",
  "LPG",
  "GRAPH_FRAME",
];

// ── Schema registry ───────────────────────────────────────────────────────
export interface SchemaLevel {
  name: string;
  depth: number;
  rollupTo: string | null;
  rollupFunction: string | null;
}

export interface SchemaDimension {
  name: string;
  levels: SchemaLevel[];
}

/** GET /api/schemas/{id} */
export interface SchemaResponse {
  id: string;
  dimensions: SchemaDimension[];
}

/** GET /api/schemas */
export interface SchemaListResponse {
  schemas: string[];
}

// ── Query requests ────────────────────────────────────────────────────────
/** Structured query → POST /api/schemas/{id}/query/structured (JSON body). */
export interface StructuredQueryRequest {
  select?: Record<string, Record<string, string>>;
  rollup?: Record<string, string>;
  representation?: GraphRepresentation;
  format?: string;
  reasoning?: boolean;
}

/** Text query → POST /api/schemas/{id}/query (text/plain body + query params). */
export interface TextQueryRequest {
  query: string;
  representation?: GraphRepresentation;
  reasoning?: boolean;
}

// ── NDJSON stream lines ───────────────────────────────────────────────────
export interface QueryTimings {
  contextResolutionMs: number;
  mergeMs: number;
  graphConstructionMs: number;
  totalMs: number;
  cacheHits: number;
  cacheMisses: number;
}

export interface SummaryLine {
  _type: "summary";
  success: boolean;
  contextCount: number;
  finalContextCount: number;
  quadCount: number;
  timings: QueryTimings;
  traceId: string | null;
}

export interface ErrorLine {
  _type: "error";
  message: string;
}

// ── Logs (GET /api/schemas/{id}/logs/{query,ingestion}) ────────────────────
export interface QueryLogDto {
  id: string;
  schemaId: string;
  queryText: string;
  contextResolveMs: number;
  graphConstructMs: number;
  mergeMs: number;
  totalMs: number;
  contextsCount: number;
  quadsCount: number;
  cacheHits: number;
  cacheMisses: number;
  success: boolean;
  errorMessage: string | null;
  completedAt: string;
}

export interface IngestionLogDto {
  id: string;
  schemaId: string;
  storedName: string;
  engineId: string;
  analysisMs: number;
  indexWriteMs: number;
  totalMs: number;
  contextsCount: number;
  completedAt: string;
}

export interface QueryLogsResponse {
  logs: QueryLogDto[];
}

export interface IngestionLogsResponse {
  logs: IngestionLogDto[];
}

// ── Stats / ingest / health ────────────────────────────────────────────────
export interface StatsResponse {
  schemaId: string;
  totalHierarchies: number;
  totalContexts: number;
  totalFiles: number;
  totalStoredFileSizeBytes: number;
}

export interface IngestionResponse {
  schemaId: string;
  storedName: string;
  taskId: string;
  engineId: string;
  originalName: string;
  sizeBytes: number;
  traceId: string;
}

export interface HealthStatus {
  status: string;
  [key: string]: unknown;
}
