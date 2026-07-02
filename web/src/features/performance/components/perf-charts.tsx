"use client";

import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from "recharts";
import type { PerfAggregate } from "../lib/aggregate";

const AXIS = { fontSize: 10, fill: "var(--muted-foreground)" };
const GRID = "var(--border)";

const COLORS = {
  total: "#5b8def",
  resolve: "#d6a44c",
  build: "#5b8def",
  merge: "#3fb98f",
};

function ChartFrame({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <h3 className="mb-3 text-xs font-semibold">{title}</h3>
      <div className="h-56">
        <ResponsiveContainer width="100%" height="100%">
          {children as React.ReactElement}
        </ResponsiveContainer>
      </div>
    </div>
  );
}

export function LatencyTrend({ data }: { data: PerfAggregate["trend"] }) {
  return (
    <ChartFrame title="Total latency per query (ms)">
      <LineChart data={data} margin={{ top: 5, right: 10, left: -10, bottom: 0 }}>
        <CartesianGrid stroke={GRID} strokeDasharray="3 3" vertical={false} />
        <XAxis dataKey="idx" tick={AXIS} stroke={GRID} />
        <YAxis tick={AXIS} stroke={GRID} />
        <Tooltip
          contentStyle={{
            background: "var(--popover)",
            border: "1px solid var(--border)",
            borderRadius: 8,
            fontSize: 12,
          }}
        />
        <Line
          type="monotone"
          dataKey="totalMs"
          stroke={COLORS.total}
          strokeWidth={2}
          dot={false}
        />
      </LineChart>
    </ChartFrame>
  );
}

export function PhaseBreakdown({
  data,
}: {
  data: PerfAggregate["breakdown"];
}) {
  return (
    <ChartFrame title="Phase breakdown — recent queries (ms)">
      <BarChart data={data} margin={{ top: 5, right: 10, left: -10, bottom: 0 }}>
        <CartesianGrid stroke={GRID} strokeDasharray="3 3" vertical={false} />
        <XAxis dataKey="idx" tick={AXIS} stroke={GRID} />
        <YAxis tick={AXIS} stroke={GRID} />
        <Tooltip
          contentStyle={{
            background: "var(--popover)",
            border: "1px solid var(--border)",
            borderRadius: 8,
            fontSize: 12,
          }}
        />
        <Legend wrapperStyle={{ fontSize: 11 }} />
        <Bar dataKey="resolve" stackId="a" fill={COLORS.resolve} name="resolve" />
        <Bar dataKey="build" stackId="a" fill={COLORS.build} name="build" />
        <Bar dataKey="merge" stackId="a" fill={COLORS.merge} name="merge" />
      </BarChart>
    </ChartFrame>
  );
}

export function RollupComparison({
  data,
}: {
  data: PerfAggregate["rollupComparison"];
}) {
  return (
    <ChartFrame title="Avg latency: ROLLUP vs simple (ms)">
      <BarChart data={data} margin={{ top: 5, right: 10, left: -10, bottom: 0 }}>
        <CartesianGrid stroke={GRID} strokeDasharray="3 3" vertical={false} />
        <XAxis dataKey="group" tick={AXIS} stroke={GRID} />
        <YAxis tick={AXIS} stroke={GRID} />
        <Tooltip
          contentStyle={{
            background: "var(--popover)",
            border: "1px solid var(--border)",
            borderRadius: 8,
            fontSize: 12,
          }}
        />
        <Bar dataKey="avgMs" fill={COLORS.total} name="avg ms" />
      </BarChart>
    </ChartFrame>
  );
}
