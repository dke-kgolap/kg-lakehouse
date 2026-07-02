"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Play } from "lucide-react";
import { ScreenHeader } from "@/components/layout/app-shell";
import { Select } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { PanelState } from "@/components/ui/spinner";
import { useSchemas, useQueryLogs } from "@/lib/api/hooks";
import { formatMs } from "@/lib/utils";

export function HistoryScreen() {
  const router = useRouter();
  const { data: list } = useSchemas();
  const [schemaId, setSchemaId] = useState<string | null>(null);

  useEffect(() => {
    if (!schemaId && list?.schemas?.length) setSchemaId(list.schemas[0]);
  }, [list, schemaId]);

  const { data, isLoading, isError } = useQueryLogs(schemaId, 100);
  const logs = data?.logs ?? [];

  const rerun = (queryText: string) => {
    const params = new URLSearchParams({
      schemaId: schemaId ?? "",
      q: queryText,
    });
    router.push(`/workspace?${params.toString()}`);
  };

  return (
    <>
      <ScreenHeader
        title="History"
        description="Past queries and re-run"
        actions={
          <div className="flex items-center gap-2">
            <Label htmlFor="hist-schema">Schema</Label>
            <Select
              id="hist-schema"
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
        ) : isError ? (
          <PanelState kind="error" message="Could not load query history." />
        ) : logs.length === 0 ? (
          <PanelState kind="empty" message="No queries logged yet." />
        ) : (
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-border text-[10px] uppercase tracking-wide text-muted-foreground">
                <th className="px-3 py-2 font-medium">Query</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Total</th>
                <th className="px-3 py-2 font-medium">Contexts</th>
                <th className="px-3 py-2 font-medium">Quads</th>
                <th className="px-3 py-2 font-medium">Cache</th>
                <th className="px-3 py-2 font-medium">When</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {logs.map((l) => (
                <tr
                  key={l.id}
                  className="border-b border-border/50 hover:bg-accent/40"
                >
                  <td
                    className="max-w-[36ch] truncate px-3 py-1.5 font-mono text-xs"
                    title={l.queryText}
                  >
                    {l.queryText || "—"}
                  </td>
                  <td className="px-3 py-1.5">
                    <Badge variant={l.success ? "success" : "destructive"}>
                      {l.success ? "ok" : "error"}
                    </Badge>
                  </td>
                  <td className="px-3 py-1.5 font-mono text-xs">
                    {formatMs(l.totalMs)}
                  </td>
                  <td className="px-3 py-1.5 font-mono text-xs">
                    {l.contextsCount}
                  </td>
                  <td className="px-3 py-1.5 font-mono text-xs">{l.quadsCount}</td>
                  <td className="px-3 py-1.5 font-mono text-xs">
                    {l.cacheHits}/{l.cacheHits + l.cacheMisses}
                  </td>
                  <td className="px-3 py-1.5 text-xs text-muted-foreground">
                    {l.completedAt?.slice(0, 19)}
                  </td>
                  <td className="px-3 py-1.5 text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => rerun(l.queryText)}
                      disabled={!l.queryText}
                    >
                      <Play /> Re-run
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
