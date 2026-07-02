"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./endpoints";

/** Shared server-state hooks. Consumed by multiple features. */

export function useSchemas() {
  return useQuery({
    queryKey: ["schemas"],
    queryFn: () => api.listSchemas(),
  });
}

export function useSchema(schemaId: string | null) {
  return useQuery({
    queryKey: ["schema", schemaId],
    queryFn: () => api.getSchema(schemaId as string),
    enabled: !!schemaId,
  });
}

export function useStats(schemaId: string | null) {
  return useQuery({
    queryKey: ["stats", schemaId],
    queryFn: () => api.getStats(schemaId as string),
    enabled: !!schemaId,
  });
}

export function useQueryLogs(schemaId: string | null, limit = 50) {
  return useQuery({
    queryKey: ["logs", "query", schemaId, limit],
    queryFn: () => api.getQueryLogs(schemaId as string, limit),
    enabled: !!schemaId,
  });
}

export function useIngestionLogs(schemaId: string | null, limit = 50) {
  return useQuery({
    queryKey: ["logs", "ingestion", schemaId, limit],
    queryFn: () => api.getIngestionLogs(schemaId as string, limit),
    enabled: !!schemaId,
  });
}

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: () => api.getHealth(),
    refetchInterval: 15_000,
  });
}
