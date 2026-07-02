"use client";

import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { formatMs } from "@/lib/utils";
import type { QueryStreamState } from "../hooks/use-query-stream";

function Metric({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex flex-col">
      <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
      <span className="font-mono text-xs">{value}</span>
    </div>
  );
}

export function RunSummaryStrip({ state }: { state: QueryStreamState }) {
  const { status, summary, lineCount } = state;
  const t = summary?.timings;

  return (
    <div className="flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-border bg-card px-4 py-2">
      <div className="flex items-center gap-2">
        {status === "streaming" && <Spinner />}
        {status === "idle" && (
          <Badge variant="outline">Ready</Badge>
        )}
        {status === "streaming" && (
          <Badge variant="secondary">Streaming…</Badge>
        )}
        {status === "done" && summary?.success !== false && (
          <Badge variant="success">Success</Badge>
        )}
        {(status === "error" || summary?.success === false) && (
          <Badge variant="destructive">Error</Badge>
        )}
      </div>

      <Metric label="Lines" value={lineCount} />
      {summary && (
        <>
          <Metric label="Contexts" value={summary.contextCount} />
          <Metric label="Final" value={summary.finalContextCount} />
          <Metric label="Quads" value={summary.quadCount} />
          <Metric label="Cache" value={`${summary.timings.cacheHits}/${summary.timings.cacheHits + summary.timings.cacheMisses}`} />
        </>
      )}
      {t && (
        <div className="flex items-center gap-3">
          <Metric label="Resolve" value={formatMs(t.contextResolutionMs)} />
          <Metric label="Build" value={formatMs(t.graphConstructionMs)} />
          <Metric label="Merge" value={formatMs(t.mergeMs)} />
          <Metric label="Total" value={formatMs(t.totalMs)} />
        </div>
      )}
      {summary?.traceId && (
        <Metric
          label="Trace"
          value={<span title={summary.traceId}>{summary.traceId.slice(0, 8)}</span>}
        />
      )}
    </div>
  );
}
