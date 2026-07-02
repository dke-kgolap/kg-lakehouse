import type { StructuredQueryRequest } from "@/lib/api/types";

/** A single equality predicate in the guided builder. */
export interface PredicateRow {
  dimension: string;
  level: string;
  value: string;
}

/** A single roll-up target in the guided builder. */
export interface RollupRow {
  dimension: string;
  level: string;
}

/** Build the structured request body from guided-builder rows. */
export function toStructuredRequest(
  predicates: PredicateRow[],
  rollups: RollupRow[],
): StructuredQueryRequest {
  const select: Record<string, Record<string, string>> = {};
  for (const p of predicates) {
    if (!p.dimension || !p.level || !p.value.trim()) continue;
    (select[p.dimension] ??= {})[p.level] = p.value.trim();
  }
  const rollup: Record<string, string> = {};
  for (const r of rollups) {
    if (!r.dimension || !r.level) continue;
    rollup[r.dimension] = r.level;
  }
  return { select, rollup };
}

/**
 * Render a read-only DSL preview of the guided selection, matching the text
 * grammar: `SELECT dim_level=value AND ... [ROLLUP ON dim_level, ...]`.
 */
export function toDslPreview(
  predicates: PredicateRow[],
  rollups: RollupRow[],
): string {
  const preds = predicates
    .filter((p) => p.dimension && p.level && p.value.trim())
    .map((p) => `${p.dimension}_${p.level}=${p.value.trim()}`);
  const select = preds.length ? preds.join(" AND ") : "*";
  const rolls = rollups
    .filter((r) => r.dimension && r.level)
    .map((r) => `${r.dimension}_${r.level}`);
  const rollup = rolls.length ? ` ROLLUP ON ${rolls.join(", ")}` : "";
  return `SELECT ${select}${rollup}`;
}
