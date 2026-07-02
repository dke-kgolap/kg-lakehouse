"use client";

import { useMemo, useState } from "react";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { localName, type Module } from "@/lib/rdf/modules";
import type { QueryStreamState } from "../../hooks/use-query-stream";

const MAX_ROWS = 2000;

const moduleVariant: Record<Module, "asserted" | "inferred" | "context"> = {
  asserted: "asserted",
  inferred: "inferred",
  context: "context",
};

function TermCell({ value, title }: { value: string; title?: string }) {
  return (
    <td
      className="max-w-[24ch] truncate px-3 py-1.5 align-top font-mono text-xs"
      title={title ?? value}
    >
      {value}
    </td>
  );
}

export function TableView({ state }: { state: QueryStreamState }) {
  const [filter, setFilter] = useState("");
  const isRdf = state.representation === "RDF";

  const rows = useMemo(() => {
    if (isRdf) {
      const f = filter.toLowerCase();
      return state.quads
        .filter((q) =>
          !f
            ? true
            : `${q.subject.value} ${q.predicate.value} ${q.object.value} ${q.graph.value}`
                .toLowerCase()
                .includes(f),
        )
        .slice(0, MAX_ROWS);
    }
    return [];
  }, [isRdf, state.quads, filter]);

  const jsonRows = useMemo(() => {
    if (isRdf) return [];
    const f = filter.toLowerCase();
    return state.jsonRows
      .filter((r) => (!f ? true : JSON.stringify(r).toLowerCase().includes(f)))
      .slice(0, MAX_ROWS);
  }, [isRdf, state.jsonRows, filter]);

  const total = isRdf ? state.quads.length : state.jsonRows.length;
  const shown = isRdf ? rows.length : jsonRows.length;

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-3 border-b border-border px-3 py-1.5">
        <Input
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Filter rows…"
          className="h-7 max-w-xs"
        />
        <span className="text-xs text-muted-foreground">
          {shown.toLocaleString()} / {total.toLocaleString()} rows
          {total > MAX_ROWS && " (capped)"}
        </span>
      </div>
      <div className="flex-1 overflow-auto">
        {total === 0 ? (
          <p className="p-4 text-sm text-muted-foreground">No rows.</p>
        ) : isRdf ? (
          <table className="w-full border-collapse text-left">
            <thead className="sticky top-0 bg-card">
              <tr className="border-b border-border text-[10px] uppercase tracking-wide text-muted-foreground">
                <th className="px-3 py-2 font-medium">Subject</th>
                <th className="px-3 py-2 font-medium">Predicate</th>
                <th className="px-3 py-2 font-medium">Object</th>
                <th className="px-3 py-2 font-medium">Module</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((q, i) => (
                <tr
                  key={i}
                  className="border-b border-border/50 hover:bg-accent/40"
                >
                  <TermCell value={localName(q.subject.value)} title={q.subject.value} />
                  <TermCell value={localName(q.predicate.value)} title={q.predicate.value} />
                  <TermCell
                    value={q.object.type === "literal" ? `"${q.object.value}"` : localName(q.object.value)}
                    title={q.object.value}
                  />
                  <td className="px-3 py-1.5 align-top">
                    <Badge variant={moduleVariant[q.module]}>{q.module}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <table className="w-full border-collapse text-left">
            <tbody>
              {jsonRows.map((r, i) => (
                <tr key={i} className="border-b border-border/50 hover:bg-accent/40">
                  <td className="px-3 py-1.5 font-mono text-xs">
                    {JSON.stringify(r)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
