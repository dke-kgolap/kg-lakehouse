import { Parser } from "n3";

/** Framework-neutral RDF term + quad shapes used across the result views. */
export type TermType = "iri" | "literal" | "blank";

export interface Term {
  type: TermType;
  value: string;
  datatype?: string;
  language?: string;
}

export interface Quad {
  subject: Term;
  predicate: Term;
  object: Term;
  graph: Term;
}

function toTerm(t: {
  termType: string;
  value: string;
  datatype?: { value: string };
  language?: string;
}): Term {
  if (t.termType === "Literal") {
    return {
      type: "literal",
      value: t.value,
      datatype: t.datatype?.value,
      language: t.language || undefined,
    };
  }
  if (t.termType === "BlankNode") return { type: "blank", value: t.value };
  // NamedNode (or default graph, which has empty value).
  return { type: "iri", value: t.value };
}

/**
 * Parse accumulated N-Quads text into our Quad shape. Tolerant: parse errors
 * on a chunk return what parsed successfully rather than throwing, since the
 * stream may contain a trailing non-RDF summary line that callers strip first.
 */
export function parseNQuads(text: string): Quad[] {
  if (!text.trim()) return [];
  const parser = new Parser({ format: "N-Quads" });
  const quads = parser.parse(text);
  return quads.map((q) => ({
    subject: toTerm(q.subject),
    predicate: toTerm(q.predicate),
    object: toTerm(q.object),
    graph: toTerm(q.graph),
  }));
}
