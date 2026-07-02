import type { TaggedQuad } from "./modules";
import { localName, type Module } from "./modules";

export interface GraphNode {
  id: string;
  label: string;
  kind: "iri" | "literal" | "blank";
  module: Module;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  label: string;
  module: Module;
}

export interface GraphModel {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

const RANK: Record<Module, number> = { context: 0, asserted: 1, inferred: 2 };

/**
 * Convert tagged quads into a node-link graph. IRIs/blank nodes become shared
 * nodes; each literal object becomes its own leaf node (so identical literals
 * under different subjects don't collapse). A node's module is the highest-rank
 * module that referenced it (inferred > asserted > context) for coloring.
 */
export function buildGraphModel(quads: TaggedQuad[]): GraphModel {
  const nodes = new Map<string, GraphNode>();
  const edges: GraphEdge[] = [];

  const upsert = (
    id: string,
    label: string,
    kind: GraphNode["kind"],
    module: Module,
  ) => {
    const existing = nodes.get(id);
    if (!existing) {
      nodes.set(id, { id, label, kind, module });
    } else if (RANK[module] > RANK[existing.module]) {
      existing.module = module;
    }
  };

  quads.forEach((q, i) => {
    const subjId = q.subject.value;
    upsert(
      subjId,
      localName(q.subject.value),
      q.subject.type === "blank" ? "blank" : "iri",
      q.module,
    );

    let targetId: string;
    if (q.object.type === "literal") {
      targetId = `lit:${i}`;
      upsert(targetId, q.object.value, "literal", q.module);
    } else {
      targetId = q.object.value;
      upsert(
        targetId,
        localName(q.object.value),
        q.object.type === "blank" ? "blank" : "iri",
        q.module,
      );
    }

    edges.push({
      id: `e:${i}`,
      source: subjId,
      target: targetId,
      label: localName(q.predicate.value),
      module: q.module,
    });
  });

  return { nodes: [...nodes.values()], edges };
}

/** Filter a model to a set of visible modules, keeping referenced nodes. */
export function filterByModules(
  model: GraphModel,
  visible: Set<Module>,
): GraphModel {
  const edges = model.edges.filter((e) => visible.has(e.module));
  const referenced = new Set<string>();
  edges.forEach((e) => {
    referenced.add(e.source);
    referenced.add(e.target);
  });
  const nodes = model.nodes.filter(
    (n) => visible.has(n.module) || referenced.has(n.id),
  );
  return { nodes, edges };
}
