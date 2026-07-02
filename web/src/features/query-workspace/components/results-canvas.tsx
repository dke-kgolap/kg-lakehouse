"use client";

import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Network, Table2, Boxes, FileJson } from "lucide-react";
import { GraphView } from "./views/graph-view";
import { TableView } from "./views/table-view";
import { CubeView } from "./views/cube-view";
import { RawView } from "./views/raw-view";
import type { QueryStreamState } from "../hooks/use-query-stream";

export function ResultsCanvas({ state }: { state: QueryStreamState }) {
  if (state.status === "idle") {
    return (
      <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
        Run a query to see results.
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div className="flex flex-1 items-center justify-center p-6">
        <div className="max-w-lg rounded-md border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {state.error ?? "Query failed."}
        </div>
      </div>
    );
  }

  return (
    <Tabs defaultValue="graph" className="flex min-h-0 flex-1 flex-col">
      <div className="border-b border-border px-3 py-1.5">
        <TabsList>
          <TabsTrigger value="graph">
            <Network className="size-3.5" /> Graph
          </TabsTrigger>
          <TabsTrigger value="table">
            <Table2 className="size-3.5" /> Table
          </TabsTrigger>
          <TabsTrigger value="cube">
            <Boxes className="size-3.5" /> Cube
          </TabsTrigger>
          <TabsTrigger value="raw">
            <FileJson className="size-3.5" /> Raw
          </TabsTrigger>
        </TabsList>
      </div>
      <div className="min-h-0 flex-1">
        <TabsContent value="graph" className="h-full">
          <GraphView state={state} />
        </TabsContent>
        <TabsContent value="table" className="h-full">
          <TableView state={state} />
        </TabsContent>
        <TabsContent value="cube" className="h-full">
          <CubeView state={state} />
        </TabsContent>
        <TabsContent value="raw" className="h-full">
          <RawView state={state} />
        </TabsContent>
      </div>
    </Tabs>
  );
}
