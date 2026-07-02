"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Upload, FileUp, CheckCircle2, AlertCircle } from "lucide-react";
import { ScreenHeader } from "@/components/layout/app-shell";
import { Select } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PanelState } from "@/components/ui/spinner";
import { useSchemas, useIngestionLogs } from "@/lib/api/hooks";
import { api } from "@/lib/api/endpoints";
import { formatMs } from "@/lib/utils";
import { ENGINES, type EngineId, type IngestionResponse } from "@/lib/api/types";

export function IngestionScreen() {
  const { data: list } = useSchemas();
  const [schemaId, setSchemaId] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [engine, setEngine] = useState<EngineId>("aixm");
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!schemaId && list?.schemas?.length) setSchemaId(list.schemas[0]);
  }, [list, schemaId]);

  const logs = useIngestionLogs(schemaId, 30);

  const mutation = useMutation<IngestionResponse, Error, void>({
    mutationFn: () => api.ingestFile(schemaId as string, file as File, engine),
    onSuccess: () => {
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      // Give the async pipeline a moment, then refresh the log.
      setTimeout(() => logs.refetch(), 1500);
    },
  });

  return (
    <>
      <ScreenHeader
        title="Ingestion"
        description="Upload source files and track ingestion"
        actions={
          <div className="flex items-center gap-2">
            <Label htmlFor="ingest-schema">Schema</Label>
            <Select
              id="ingest-schema"
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
        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Upload a file</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border bg-background px-4 py-8 text-center text-sm text-muted-foreground hover:border-primary/50">
                <FileUp className="size-6" />
                {file ? (
                  <span className="font-medium text-foreground">{file.name}</span>
                ) : (
                  <span>Choose an AIXM / IWXXM / FIXM file</span>
                )}
                <input
                  ref={fileInputRef}
                  type="file"
                  className="hidden"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
              </label>
              <div className="flex flex-col gap-1">
                <Label htmlFor="ingest-engine">Engine / format</Label>
                <Select
                  id="ingest-engine"
                  value={engine}
                  onChange={(e) => setEngine(e.target.value as EngineId)}
                >
                  {ENGINES.map((en) => (
                    <option key={en} value={en}>
                      {en.toUpperCase()}
                    </option>
                  ))}
                </Select>
              </div>
              <Button
                onClick={() => mutation.mutate()}
                disabled={!schemaId || !file || mutation.isPending}
              >
                <Upload />
                {mutation.isPending ? "Uploading…" : "Ingest"}
              </Button>
              {mutation.isSuccess && (
                <div className="flex items-start gap-2 rounded-md bg-emerald-500/10 px-3 py-2 text-xs text-emerald-500">
                  <CheckCircle2 className="size-4 shrink-0" />
                  <span>
                    Accepted <code>{mutation.data.originalName}</code> (task{" "}
                    {mutation.data.taskId}). Processing asynchronously.
                  </span>
                </div>
              )}
              {mutation.isError && (
                <div className="flex items-start gap-2 rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">
                  <AlertCircle className="size-4 shrink-0" />
                  <span>{mutation.error.message}</span>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle>Recent ingestions</CardTitle>
              <Button
                variant="outline"
                size="sm"
                onClick={() => logs.refetch()}
                disabled={logs.isFetching}
              >
                Refresh
              </Button>
            </CardHeader>
            <CardContent>
              {logs.isLoading ? (
                <PanelState kind="loading" />
              ) : logs.isError ? (
                <PanelState kind="error" message="Could not load logs." />
              ) : (logs.data?.logs.length ?? 0) === 0 ? (
                <PanelState kind="empty" message="No ingestions yet." />
              ) : (
                <ul className="flex flex-col divide-y divide-border">
                  {logs.data!.logs.map((l) => (
                    <li
                      key={l.id}
                      className="flex items-center justify-between gap-2 py-2 text-sm"
                    >
                      <div className="flex min-w-0 flex-col">
                        <span className="truncate font-mono text-xs">
                          {l.storedName}
                        </span>
                        <span className="text-[11px] text-muted-foreground">
                          {l.engineId} · {l.contextsCount} contexts ·{" "}
                          {formatMs(l.totalMs)}
                        </span>
                      </div>
                      <Badge variant="secondary">{l.completedAt?.slice(0, 19)}</Badge>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}
