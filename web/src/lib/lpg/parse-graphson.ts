import type { GraphModel, GraphNode, GraphEdge } from "@/lib/rdf/graph-model";

/**
 * Tolerant parser for LPG / GraphFrame stream lines. These arrive as JSON
 * objects rather than N-Quads. We handle the common shapes:
 *   - GraphFrame vertex rows:  { id, label?, ... }
 *   - GraphFrame edge rows:    { src, dst, label?, ... }  (or source/target)
 *   - TinkerPop GraphSON:      { "@type": "g:Vertex"|"g:Edge", "@value": {...} }
 * Everything is tagged module "asserted" (LPG has no -mod/-inf split), so the
 * graph view simply hides the module toggle for these representations.
 */
export function buildGraphModelFromJson(
  rows: Record<string, unknown>[],
): GraphModel {
  const nodes = new Map<string, GraphNode>();
  const edges: GraphEdge[] = [];

  const addNode = (id: string, label?: string) => {
    if (!nodes.has(id)) {
      nodes.set(id, {
        id,
        label: label ?? id,
        kind: "iri",
        module: "asserted",
      });
    }
  };

  rows.forEach((raw, i) => {
    // Unwrap GraphSON envelope if present.
    const isVertexEnvelope = raw["@type"] === "g:Vertex";
    const isEdgeEnvelope = raw["@type"] === "g:Edge";
    const obj = (raw["@value"] as Record<string, unknown>) ?? raw;

    const src = obj.src ?? obj.source ?? obj.outV;
    const dst = obj.dst ?? obj.target ?? obj.inV;
    const label = typeof obj.label === "string" ? obj.label : undefined;

    if (isEdgeEnvelope || (src != null && dst != null)) {
      const s = String(src);
      const t = String(dst);
      addNode(s);
      addNode(t);
      edges.push({
        id: `e:${i}`,
        source: s,
        target: t,
        label: label ?? "edge",
        module: "asserted",
      });
      return;
    }

    if (isVertexEnvelope || obj.id != null) {
      addNode(String(obj.id), label);
    }
  });

  return { nodes: [...nodes.values()], edges };
}
