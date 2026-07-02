import { percentile } from "@/lib/utils";
import type { QueryLogDto } from "@/lib/api/types";

export interface PerfAggregate {
  count: number;
  successCount: number;
  errorCount: number;
  latency: { p50: number; p95: number; p99: number; avg: number; max: number };
  phases: {
    contextResolveMs: number;
    graphConstructMs: number;
    mergeMs: number;
  };
  cache: { hits: number; misses: number; ratio: number };
  /** Per-query trend (oldest → newest) for the latency line chart. */
  trend: { idx: number; totalMs: number; label: string }[];
  /** Per-query phase breakdown for the stacked bar chart (most recent N). */
  breakdown: {
    idx: number;
    resolve: number;
    build: number;
    merge: number;
    label: string;
  }[];
  /** ROLLUP vs simple cost comparison (derivable from queryText). */
  rollupComparison: {
    group: "rollup" | "simple";
    avgMs: number;
    count: number;
  }[];
}

function ts(log: QueryLogDto): number {
  const t = Date.parse(log.completedAt);
  return Number.isNaN(t) ? 0 : t;
}

export function aggregate(logs: QueryLogDto[]): PerfAggregate {
  const ordered = [...logs].sort((a, b) => ts(a) - ts(b));
  const ok = ordered.filter((l) => l.success);

  const latencies = ok.map((l) => l.totalMs).sort((a, b) => a - b);
  const sum = (xs: number[]) => xs.reduce((s, x) => s + x, 0);
  const avg = (xs: number[]) => (xs.length ? sum(xs) / xs.length : 0);

  const hits = sum(ok.map((l) => l.cacheHits));
  const misses = sum(ok.map((l) => l.cacheMisses));

  const rollup = ok.filter((l) => /\brollup\b/i.test(l.queryText ?? ""));
  const simple = ok.filter((l) => !/\brollup\b/i.test(l.queryText ?? ""));

  const recent = ok.slice(-20);

  return {
    count: ordered.length,
    successCount: ok.length,
    errorCount: ordered.length - ok.length,
    latency: {
      p50: percentile(latencies, 50),
      p95: percentile(latencies, 95),
      p99: percentile(latencies, 99),
      avg: avg(latencies),
      max: latencies.length ? latencies[latencies.length - 1] : 0,
    },
    phases: {
      contextResolveMs: avg(ok.map((l) => l.contextResolveMs)),
      graphConstructMs: avg(ok.map((l) => l.graphConstructMs)),
      mergeMs: avg(ok.map((l) => l.mergeMs)),
    },
    cache: {
      hits,
      misses,
      ratio: hits + misses === 0 ? 0 : hits / (hits + misses),
    },
    trend: ok.map((l, i) => ({
      idx: i + 1,
      totalMs: l.totalMs,
      label: l.queryText ?? `query ${i + 1}`,
    })),
    breakdown: recent.map((l, i) => ({
      idx: i + 1,
      resolve: l.contextResolveMs,
      build: l.graphConstructMs,
      merge: l.mergeMs,
      label: l.queryText ?? `query ${i + 1}`,
    })),
    rollupComparison: [
      { group: "rollup", avgMs: avg(rollup.map((l) => l.totalMs)), count: rollup.length },
      { group: "simple", avgMs: avg(simple.map((l) => l.totalMs)), count: simple.length },
    ],
  };
}
