"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { Core, ElementDefinition } from "cytoscape";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import {
  buildGraphModel,
  filterByModules,
  type GraphModel,
} from "@/lib/rdf/graph-model";
import { buildGraphModelFromJson } from "@/lib/lpg/parse-graphson";
import type { Module } from "@/lib/rdf/modules";
import type { QueryStreamState } from "../../hooks/use-query-stream";

const MAX_ELEMENTS = 1200;

const MODULE_COLOR: Record<Module, string> = {
  asserted: "#5b8def",
  inferred: "#3fb98f",
  context: "#d6a44c",
};
const LITERAL_COLOR = "#8a8f98";

export function GraphView({ state }: { state: QueryStreamState }) {
  const isRdf = state.representation === "RDF";

  const fullModel: GraphModel = useMemo(
    () =>
      isRdf
        ? buildGraphModel(state.quads)
        : buildGraphModelFromJson(state.jsonRows),
    [isRdf, state.quads, state.jsonRows],
  );

  const [visible, setVisible] = useState<Record<Module, boolean>>({
    asserted: true,
    inferred: true,
    context: true,
  });

  const model = useMemo(() => {
    const set = new Set<Module>(
      (Object.keys(visible) as Module[]).filter((m) => visible[m]),
    );
    return isRdf ? filterByModules(fullModel, set) : fullModel;
  }, [fullModel, visible, isRdf]);

  const elements = useMemo<ElementDefinition[]>(() => {
    const nodes = model.nodes.slice(0, MAX_ELEMENTS);
    const nodeIds = new Set(nodes.map((n) => n.id));
    const nodeEls: ElementDefinition[] = nodes.map((n) => ({
      data: { id: n.id, label: n.label },
      classes: n.kind === "literal" ? "literal" : n.module,
    }));
    const edgeEls: ElementDefinition[] = model.edges
      .filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target))
      .map((e) => ({
        data: {
          id: e.id,
          source: e.source,
          target: e.target,
          label: e.label,
        },
        classes: e.module,
      }));
    return [...nodeEls, ...edgeEls];
  }, [model]);

  const isCapped = model.nodes.length > MAX_ELEMENTS;
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);

  useEffect(() => {
    let disposed = false;
    let cy: Core | null = null;
    let ro: ResizeObserver | null = null;

    (async () => {
      const cytoscape = (await import("cytoscape")).default;
      if (disposed || !containerRef.current) return;

      cy = cytoscape({
        container: containerRef.current,
        elements,
        style: [
          {
            selector: "node",
            style: {
              label: "data(label)",
              color: "#e5e7eb",
              "font-size": 8,
              "text-valign": "center",
              "text-halign": "center",
              "text-outline-color": "#16181d",
              "text-outline-width": 2,
              width: 18,
              height: 18,
            },
          },
          { selector: "node.context", style: { "background-color": MODULE_COLOR.context } },
          { selector: "node.asserted", style: { "background-color": MODULE_COLOR.asserted } },
          { selector: "node.inferred", style: { "background-color": MODULE_COLOR.inferred } },
          {
            selector: "node.literal",
            style: {
              "background-color": LITERAL_COLOR,
              shape: "round-rectangle",
              width: 24,
              height: 14,
            },
          },
          {
            selector: "edge",
            style: {
              width: 1,
              "target-arrow-shape": "triangle",
              "curve-style": "bezier",
              label: "data(label)",
              "font-size": 6,
              color: "#9aa0aa",
              "text-rotation": "autorotate",
            },
          },
          {
            selector: "edge.context",
            style: { "line-color": MODULE_COLOR.context, "target-arrow-color": MODULE_COLOR.context },
          },
          {
            selector: "edge.asserted",
            style: { "line-color": MODULE_COLOR.asserted, "target-arrow-color": MODULE_COLOR.asserted },
          },
          {
            selector: "edge.inferred",
            style: { "line-color": MODULE_COLOR.inferred, "target-arrow-color": MODULE_COLOR.inferred },
          },
        ],
        layout: { name: "cose", animate: false, padding: 20 },
      });
      cyRef.current = cy;

      // With animate:false the cose layout completes synchronously, so fit the
      // viewport explicitly (after a frame, once the container has its size)
      // and again whenever the container resizes.
      const fit = () => {
        if (disposed || !cy) return;
        cy.resize();
        cy.fit(undefined, 30);
      };
      requestAnimationFrame(fit);

      const el = containerRef.current;
      if (el && "ResizeObserver" in window) {
        ro = new ResizeObserver(fit);
        ro.observe(el);
      }
    })();

    return () => {
      disposed = true;
      ro?.disconnect();
      cy?.destroy();
      cyRef.current = null;
    };
  }, [elements]);

  const total = fullModel.nodes.length;

  return (
    <div className="flex h-full flex-col">
      <div className="flex flex-wrap items-center gap-4 border-b border-border px-3 py-1.5">
        {isRdf ? (
          <div className="flex items-center gap-3">
            {(["asserted", "inferred", "context"] as Module[]).map((m) => (
              <label key={m} className="flex items-center gap-1.5 text-xs">
                <Switch
                  checked={visible[m]}
                  onCheckedChange={(v) =>
                    setVisible((prev) => ({ ...prev, [m]: v }))
                  }
                />
                <span
                  className="inline-block size-2 rounded-full"
                  style={{ background: MODULE_COLOR[m] }}
                />
                {m}
              </label>
            ))}
          </div>
        ) : (
          <span className="text-xs text-muted-foreground">
            Labeled property graph
          </span>
        )}
        <span className="ml-auto text-xs text-muted-foreground">
          {total.toLocaleString()} nodes
        </span>
        {isCapped && <Badge variant="outline">view capped</Badge>}
      </div>
      <div className="relative flex-1">
        {total === 0 && (
          <p className="absolute inset-0 flex items-center justify-center text-sm text-muted-foreground">
            No graph to display.
          </p>
        )}
        <div ref={containerRef} className="h-full w-full" />
      </div>
    </div>
  );
}
