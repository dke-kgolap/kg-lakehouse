"use client";

import { useEffect, useMemo, useState } from "react";
import { ScreenHeader } from "@/components/layout/app-shell";
import { Select } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PanelState } from "@/components/ui/spinner";
import { useSchemas, useQueryLogs } from "@/lib/api/hooks";
import { formatMs } from "@/lib/utils";
import { aggregate } from "./lib/aggregate";
import {
  LatencyTrend,
  PhaseBreakdown,
  RollupComparison,
} from "./components/perf-charts";

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xs text-muted-foreground">{label}</CardTitle>
      </CardHeader>
      <CardContent className="font-mono text-lg">{value}</CardContent>
    </Card>
  );
}

export function PerformanceScreen() {
  const { data: list } = useSchemas();
  const [schemaId, setSchemaId] = useState<string | null>(null);

  useEffect(() => {
    if (!schemaId && list?.schemas?.length) setSchemaId(list.schemas[0]);
  }, [list, schemaId]);

  const { data, isLoading, isError } = useQueryLogs(schemaId, 200);
  const agg = useMemo(
    () => (data ? aggregate(data.logs) : null),
    [data],
  );

  return (
    <>
      <ScreenHeader
        title="Performance"
        description="Per-query timing and cache analytics (from query logs)"
        actions={
          <div className="flex items-center gap-2">
            <Label htmlFor="perf-schema">Schema</Label>
            <Select
              id="perf-schema"
              value={schemaId ?? ""}
              onChange={(e) => setSchemaId(e.target.value)}
              className="w-48"
            >
              <option value="" disabled>
                Select a schema
              </option>
              {(list?.schemas ?? []).map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </div>
        }
      />
      <div className="flex-1 overflow-auto p-6">
        {!schemaId ? (
          <PanelState kind="empty" message="Select a schema." />
        ) : isLoading ? (
          <PanelState kind="loading" />
        ) : isError || !agg ? (
          <PanelState kind="error" message="Could not load query logs." />
        ) : agg.count === 0 ? (
          <PanelState
            kind="empty"
            message="No queries logged yet. Run some queries in the workspace."
          />
        ) : (
          <div className="flex flex-col gap-6">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              <Kpi label="Queries" value={agg.count.toLocaleString()} />
              <Kpi label="p50" value={formatMs(agg.latency.p50)} />
              <Kpi label="p95" value={formatMs(agg.latency.p95)} />
              <Kpi
                label="Cache hit"
                value={`${Math.round(agg.cache.ratio * 100)}%`}
              />
              <Kpi label="Errors" value={agg.errorCount.toLocaleString()} />
            </div>
            <div className="grid gap-4 lg:grid-cols-2">
              <LatencyTrend data={agg.trend} />
              <PhaseBreakdown data={agg.breakdown} />
              <RollupComparison data={agg.rollupComparison} />
              <div className="rounded-lg border border-border bg-card p-4">
                <h3 className="mb-3 text-xs font-semibold">Latency summary</h3>
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  {(
                    [
                      ["p50", agg.latency.p50],
                      ["p95", agg.latency.p95],
                      ["p99", agg.latency.p99],
                      ["avg", agg.latency.avg],
                      ["max", agg.latency.max],
                    ] as const
                  ).map(([k, v]) => (
                    <div
                      key={k}
                      className="flex items-center justify-between border-b border-border/50 pb-1"
                    >
                      <dt className="text-muted-foreground">{k}</dt>
                      <dd className="font-mono">{formatMs(v)}</dd>
                    </div>
                  ))}
                </dl>
                <p className="mt-3 text-[11px] text-muted-foreground">
                  Reasoning on/off is not recorded in query logs; the ROLLUP vs
                  simple split is derived from query text.
                </p>
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
