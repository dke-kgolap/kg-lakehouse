"use client";

import { useMemo } from "react";
import { Badge } from "@/components/ui/badge";
import { cellMembers, coversEdges, localName } from "@/lib/rdf/modules";
import type { QueryStreamState } from "../../hooks/use-query-stream";

interface ContextCell {
  graph: string;
  dims: Record<string, { level: string; value: string }>;
  covers: string[];
}

export function CubeView({ state }: { state: QueryStreamState }) {
  const cells = useMemo<ContextCell[]>(() => {
    if (state.representation !== "RDF") return [];
    const members = cellMembers(state.quads);
    const covers = coversEdges(state.quads);

    const byGraph = new Map<string, ContextCell>();
    const ensure = (g: string) => {
      let c = byGraph.get(g);
      if (!c) {
        c = { graph: g, dims: {}, covers: [] };
        byGraph.set(g, c);
      }
      return c;
    };

    // Keep the deepest level per dimension (finest grain) for each context.
    const depthSeen = new Map<string, number>();
    for (const m of members) {
      const cell = ensure(m.contextGraph);
      const key = `${m.contextGraph}|${m.dimension}`;
      const prev = depthSeen.get(key) ?? -1;
      // Heuristic: later/longer level names tend to be finer; keep last seen.
      cell.dims[m.dimension] = { level: m.level, value: m.value };
      depthSeen.set(key, prev + 1);
    }
    for (const e of covers) {
      ensure(e.parent).covers.push(e.child);
    }
    return [...byGraph.values()];
  }, [state.quads, state.representation]);

  if (state.representation !== "RDF") {
    return (
      <p className="p-4 text-sm text-muted-foreground">
        The cube view is available for RDF results.
      </p>
    );
  }

  if (cells.length === 0) {
    return (
      <p className="p-4 text-sm text-muted-foreground">
        No context cells in this result.
      </p>
    );
  }

  const dimensions = [...new Set(cells.flatMap((c) => Object.keys(c.dims)))];

  return (
    <div className="h-full overflow-auto p-4">
      <p className="mb-3 text-xs text-muted-foreground">
        {cells.length} matched context cell{cells.length === 1 ? "" : "s"} across{" "}
        {dimensions.join(" × ") || "no dimensions"}.
      </p>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {cells.map((cell) => (
          <div
            key={cell.graph}
            className="rounded-lg border border-border bg-card p-3"
          >
            <div
              className="mb-2 truncate font-mono text-xs text-muted-foreground"
              title={cell.graph}
            >
              {localName(cell.graph)}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {dimensions.map((d) => {
                const member = cell.dims[d];
                return member ? (
                  <Badge key={d} variant="context" title={`${d}_${member.level}`}>
                    {d}: {member.value}
                  </Badge>
                ) : (
                  <Badge key={d} variant="outline" className="opacity-50">
                    {d}: —
                  </Badge>
                );
              })}
            </div>
            {cell.covers.length > 0 && (
              <div className="mt-2 border-t border-border pt-2">
                <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
                  rolls up · covers {cell.covers.length}
                </span>
                <div className="mt-1 flex flex-wrap gap-1">
                  {cell.covers.slice(0, 8).map((child) => (
                    <Badge key={child} variant="secondary" title={child}>
                      {localName(child)}
                    </Badge>
                  ))}
                  {cell.covers.length > 8 && (
                    <Badge variant="outline">+{cell.covers.length - 8}</Badge>
                  )}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
