"use client";

import { useCallback, useRef, useState } from "react";
import { readLines } from "@/lib/ndjson/line-reader";
import { classifyLine } from "@/lib/ndjson/parse-line";
import { parseNQuads } from "@/lib/rdf/parse-nquads";
import { tagModules, type TaggedQuad } from "@/lib/rdf/modules";
import type {
  GraphRepresentation,
  StructuredQueryRequest,
  SummaryLine,
} from "@/lib/api/types";

export type QueryMode = "text" | "structured";

export interface QueryStreamRequest {
  schemaId: string;
  representation: GraphRepresentation;
  reasoning: boolean;
  mode: QueryMode;
  text?: string;
  structured?: StructuredQueryRequest;
}

export type StreamStatus = "idle" | "streaming" | "done" | "error";

export interface QueryStreamState {
  status: StreamStatus;
  representation: GraphRepresentation;
  quads: TaggedQuad[];
  jsonRows: Record<string, unknown>[];
  rawLines: string[];
  summary: SummaryLine | null;
  error: string | null;
  lineCount: number;
}

const INITIAL: QueryStreamState = {
  status: "idle",
  representation: "RDF",
  quads: [],
  jsonRows: [],
  rawLines: [],
  summary: null,
  error: null,
  lineCount: 0,
};

const FLUSH_EVERY = 150;

export function useQueryStream() {
  const [state, setState] = useState<QueryStreamState>(INITIAL);
  const abortRef = useRef<AbortController | null>(null);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const reset = useCallback(() => {
    cancel();
    setState(INITIAL);
  }, [cancel]);

  const run = useCallback(
    async (req: QueryStreamRequest) => {
      cancel();
      const controller = new AbortController();
      abortRef.current = controller;

      setState({ ...INITIAL, status: "streaming", representation: req.representation });

      // Accumulators kept outside React state; flushed in batches.
      const quads: TaggedQuad[] = [];
      const jsonRows: Record<string, unknown>[] = [];
      const rawLines: string[] = [];
      let summary: SummaryLine | null = null;
      let error: string | null = null;
      let count = 0;

      const flush = () =>
        setState((prev) => ({
          ...prev,
          quads: quads.slice(),
          jsonRows: jsonRows.slice(),
          rawLines: rawLines.slice(),
          lineCount: count,
        }));

      try {
        const res = await fetchQuery(req, controller.signal);
        if (!res.ok || !res.body) {
          const msg = await safeErrorMessage(res);
          setState((prev) => ({ ...prev, status: "error", error: msg }));
          return;
        }

        for await (const line of readLines(res.body, controller.signal)) {
          const event = classifyLine(line);
          switch (event.kind) {
            case "skip":
              break;
            case "summary":
              summary = event.summary;
              break;
            case "error":
              error = event.error.message;
              break;
            case "quad": {
              rawLines.push(line);
              count++;
              if (req.representation === "RDF") {
                try {
                  quads.push(...tagModules(parseNQuads(line)));
                } catch {
                  /* tolerate a malformed line */
                }
              }
              break;
            }
            case "json": {
              rawLines.push(line);
              count++;
              jsonRows.push(event.value);
              break;
            }
          }
          if (count % FLUSH_EVERY === 0) flush();
        }

        if (controller.signal.aborted) return;

        setState((prev) => ({
          ...prev,
          status: error ? "error" : "done",
          quads: quads.slice(),
          jsonRows: jsonRows.slice(),
          rawLines: rawLines.slice(),
          summary,
          error,
          lineCount: count,
        }));
      } catch (err) {
        if (controller.signal.aborted) return;
        setState((prev) => ({
          ...prev,
          status: "error",
          error: err instanceof Error ? err.message : String(err),
        }));
      } finally {
        if (abortRef.current === controller) abortRef.current = null;
      }
    },
    [cancel],
  );

  return { state, run, cancel, reset };
}

async function fetchQuery(
  req: QueryStreamRequest,
  signal: AbortSignal,
): Promise<Response> {
  const base = `/api/schemas/${encodeURIComponent(req.schemaId)}`;
  if (req.mode === "text") {
    const params = new URLSearchParams({
      representation: req.representation,
      reasoning: String(req.reasoning),
    });
    return fetch(`${base}/query?${params}`, {
      method: "POST",
      headers: { "content-type": "text/plain" },
      body: req.text ?? "",
      signal,
    });
  }
  const body: StructuredQueryRequest = {
    ...(req.structured ?? {}),
    representation: req.representation,
    reasoning: req.reasoning,
  };
  return fetch(`${base}/query/structured`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
    signal,
  });
}

async function safeErrorMessage(res: Response): Promise<string> {
  try {
    const data = await res.json();
    return (
      (data as { message?: string; detail?: string }).message ??
      (data as { detail?: string }).detail ??
      `Query failed (HTTP ${res.status})`
    );
  } catch {
    return `Query failed (HTTP ${res.status})`;
  }
}
