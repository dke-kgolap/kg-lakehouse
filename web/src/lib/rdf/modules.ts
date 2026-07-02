import type { Quad } from "./parse-nquads";

/**
 * CKR / OLAP vocabulary (verified against CkrVocabulary.java).
 * The 3-module named-graph split is decided by the graph IRI suffix.
 */
export const CKR_NS = "http://dkm.fbk.eu/ckr/meta#";
export const OLAP_NS = "http://dkm.fbk.eu/ckr/olap-model#";
export const COVERS = OLAP_NS + "covers";
export const ROLLS_UP_TO = OLAP_NS + "rollsUpTo";
export const LAKEHOUSE_NS = "urn:lakehouse:";
const MOD_SUFFIX = "-mod";
const INF_SUFFIX = "-inf";

export type Module = "asserted" | "inferred" | "context";

export function moduleOf(graphIri: string): Module {
  if (graphIri.endsWith(INF_SUFFIX)) return "inferred";
  if (graphIri.endsWith(MOD_SUFFIX)) return "asserted";
  return "context";
}

export interface TaggedQuad extends Quad {
  module: Module;
}

export function tagModules(quads: Quad[]): TaggedQuad[] {
  return quads.map((q) => ({ ...q, module: moduleOf(q.graph.value) }));
}

/** A roll-up edge: a merged context graph "covers" a leaf context graph. */
export interface CoversEdge {
  parent: string;
  child: string;
}

export function coversEdges(quads: Quad[]): CoversEdge[] {
  return quads
    .filter((q) => q.predicate.value === COVERS || q.predicate.value === ROLLS_UP_TO)
    .map((q) => ({ parent: q.subject.value, child: q.object.value }));
}

/**
 * Dimensional membership: quads in a context graph whose predicate looks like
 * `urn:lakehouse:{dimension}_{level}` with a literal value. These describe
 * which OLAP cell a context belongs to.
 */
export interface CellMember {
  contextGraph: string;
  dimension: string;
  level: string;
  value: string;
}

export function cellMembers(quads: TaggedQuad[]): CellMember[] {
  const out: CellMember[] = [];
  for (const q of quads) {
    if (q.module !== "context") continue;
    if (!q.predicate.value.startsWith(LAKEHOUSE_NS)) continue;
    if (q.object.type !== "literal") continue;
    const local = q.predicate.value.slice(LAKEHOUSE_NS.length);
    const underscore = local.indexOf("_");
    if (underscore <= 0) continue; // skip non-dimension predicates
    out.push({
      contextGraph: q.graph.value,
      dimension: local.slice(0, underscore),
      level: local.slice(underscore + 1),
      value: q.object.value,
    });
  }
  return out;
}

/** Short, human-readable label for an IRI/term value. */
export function localName(iri: string): string {
  const hash = iri.lastIndexOf("#");
  if (hash >= 0) return iri.slice(hash + 1);
  const slash = iri.lastIndexOf("/");
  if (slash >= 0 && slash < iri.length - 1) return iri.slice(slash + 1);
  const colon = iri.lastIndexOf(":");
  if (colon >= 0 && colon < iri.length - 1) return iri.slice(colon + 1);
  return iri;
}
