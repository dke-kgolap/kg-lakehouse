"use client";

import { useEffect, useState } from "react";
import { Play, Square } from "lucide-react";
import { ScreenHeader } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { SchemaPicker } from "./components/schema-picker";
import { QueryToggles } from "./components/query-toggles";
import { DslEditor } from "./components/dsl-editor";
import { GuidedBuilder } from "./components/guided-builder";
import { ResultsCanvas } from "./components/results-canvas";
import { RunSummaryStrip } from "./components/run-summary-strip";
import { useQueryStream, type QueryMode } from "./hooks/use-query-stream";
import { toStructuredRequest, type PredicateRow, type RollupRow } from "./model/query-request";
import type { GraphRepresentation } from "@/lib/api/types";

export function QueryWorkspaceScreen() {
  const [schemaId, setSchemaId] = useState<string | null>(null);
  const [mode, setMode] = useState<QueryMode>("structured");
  const [dsl, setDsl] = useState("SELECT *");
  const [predicates, setPredicates] = useState<PredicateRow[]>([]);
  const [rollups, setRollups] = useState<RollupRow[]>([]);
  const [representation, setRepresentation] =
    useState<GraphRepresentation>("RDF");
  const [reasoning, setReasoning] = useState(false);

  const { state, run, cancel } = useQueryStream();

  // Prefill from a History "re-run" deep link (?schemaId=…&q=…). Read from the
  // URL directly to avoid forcing a Suspense boundary via useSearchParams.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const sid = params.get("schemaId");
    const q = params.get("q");
    if (sid) setSchemaId(sid);
    if (q) {
      setDsl(q);
      setMode("text");
    }
  }, []);

  const canRun = !!schemaId && state.status !== "streaming";

  const onRun = () => {
    if (!schemaId) return;
    run({
      schemaId,
      representation,
      reasoning,
      mode,
      text: dsl,
      structured: toStructuredRequest(predicates, rollups),
    });
  };

  return (
    <>
      <ScreenHeader
        title="Query Workspace"
        description="Compose queries and visualize results"
        actions={
          state.status === "streaming" ? (
            <Button variant="outline" size="sm" onClick={cancel}>
              <Square /> Stop
            </Button>
          ) : (
            <Button size="sm" onClick={onRun} disabled={!canRun}>
              <Play /> Run query
            </Button>
          )
        }
      />
      <div className="flex min-h-0 flex-1">
        {/* Builder panel */}
        <div className="flex w-[380px] shrink-0 flex-col gap-4 overflow-y-auto border-r border-border p-4">
          <SchemaPicker value={schemaId} onChange={setSchemaId} />
          <QueryToggles
            representation={representation}
            onRepresentation={setRepresentation}
            reasoning={reasoning}
            onReasoning={setReasoning}
          />
          <Tabs
            value={mode}
            onValueChange={(v) => setMode(v as QueryMode)}
            className="flex flex-col gap-3"
          >
            <TabsList>
              <TabsTrigger value="structured">Guided</TabsTrigger>
              <TabsTrigger value="text">DSL</TabsTrigger>
            </TabsList>
            <TabsContent value="structured">
              <GuidedBuilder
                schemaId={schemaId}
                predicates={predicates}
                setPredicates={setPredicates}
                rollups={rollups}
                setRollups={setRollups}
              />
            </TabsContent>
            <TabsContent value="text">
              <DslEditor value={dsl} onChange={setDsl} />
            </TabsContent>
          </Tabs>
        </div>

        {/* Results panel */}
        <div className="flex min-w-0 flex-1 flex-col">
          <ResultsCanvas state={state} />
          <RunSummaryStrip state={state} />
        </div>
      </div>
    </>
  );
}
