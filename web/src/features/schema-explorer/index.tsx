"use client";

import { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { ScreenHeader } from "@/components/layout/app-shell";
import { Select } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PanelState } from "@/components/ui/spinner";
import { useSchemas, useSchema, useStats } from "@/lib/api/hooks";
import { DimensionCard } from "./components/dimension-card";
import { RegisterSchema } from "./components/register-schema";

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xs text-muted-foreground">{label}</CardTitle>
      </CardHeader>
      <CardContent className="font-mono text-lg">{value}</CardContent>
    </Card>
  );
}

export function SchemaExplorerScreen() {
  const { data: list, isLoading: listLoading, isError: listError } = useSchemas();
  const [schemaId, setSchemaId] = useState<string | null>(null);
  const [showRegister, setShowRegister] = useState(false);

  // Default to the first schema once the list loads.
  useEffect(() => {
    if (!schemaId && list?.schemas?.length) setSchemaId(list.schemas[0]);
  }, [list, schemaId]);

  const { data: schema, isLoading, isError } = useSchema(schemaId);
  const { data: stats } = useStats(schemaId);

  const noSchemas = !listLoading && !listError && (list?.schemas?.length ?? 0) === 0;

  return (
    <>
      <ScreenHeader
        title="Schema Explorer"
        description="Browse dimensions, levels, and hierarchies"
        actions={
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <Label htmlFor="schema-select">Schema</Label>
              <Select
                id="schema-select"
                value={schemaId ?? ""}
                onChange={(e) => setSchemaId(e.target.value)}
                disabled={listLoading || listError}
                className="w-48"
              >
                <option value="" disabled>
                  {listLoading
                    ? "Loading…"
                    : listError
                      ? "Surface unreachable"
                      : noSchemas
                        ? "No schemas yet"
                        : "Select a schema"}
                </option>
                {(list?.schemas ?? []).map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </Select>
            </div>
            <Button size="sm" onClick={() => setShowRegister((v) => !v)}>
              <Plus /> Register schema
            </Button>
          </div>
        }
      />
      <div className="flex-1 overflow-auto p-6">
        <div className="flex flex-col gap-6">
          {showRegister && (
            <RegisterSchema
              onClose={() => setShowRegister(false)}
              onRegistered={(id) => {
                setSchemaId(id);
                setShowRegister(false);
              }}
            />
          )}

          {listError ? (
            <PanelState
              kind="error"
              message="Surface unreachable — check that the backend is running and you're signed in."
            />
          ) : noSchemas && !showRegister ? (
            <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
              <p className="text-sm text-muted-foreground">
                No schemas registered yet.
              </p>
              <Button onClick={() => setShowRegister(true)}>
                <Plus /> Register your first schema
              </Button>
            </div>
          ) : !schemaId ? (
            !showRegister && (
              <PanelState kind="empty" message="Select a schema to explore." />
            )
          ) : isLoading ? (
            <PanelState kind="loading" />
          ) : isError || !schema ? (
            <PanelState kind="error" message="Could not load schema." />
          ) : (
            <>
              {stats && (
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                  <StatCard
                    label="Contexts"
                    value={stats.totalContexts.toLocaleString()}
                  />
                  <StatCard
                    label="Hierarchies"
                    value={stats.totalHierarchies.toLocaleString()}
                  />
                  <StatCard
                    label="Files"
                    value={stats.totalFiles.toLocaleString()}
                  />
                  <StatCard
                    label="Stored bytes"
                    value={stats.totalStoredFileSizeBytes.toLocaleString()}
                  />
                </div>
              )}
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {schema.dimensions.map((d) => (
                  <DimensionCard key={d.name} dimension={d} />
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}
