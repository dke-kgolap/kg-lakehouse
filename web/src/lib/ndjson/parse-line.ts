import type { SummaryLine, ErrorLine } from "@/lib/api/types";

/**
 * A single classified NDJSON line from a query stream. Data lines are either
 * raw N-Quad text (RDF) or a parsed JSON object (GraphSON, for LPG/GraphFrame).
 * The trailing summary line and any error line are recognized by `_type`.
 */
export type LineEvent =
  | { kind: "summary"; summary: SummaryLine }
  | { kind: "error"; error: ErrorLine }
  | { kind: "quad"; text: string }
  | { kind: "json"; value: Record<string, unknown> }
  | { kind: "skip" };

export function classifyLine(raw: string): LineEvent {
  const line = raw.trim();
  if (!line) return { kind: "skip" };

  if (line.startsWith("{")) {
    try {
      const value = JSON.parse(line) as Record<string, unknown>;
      if (value._type === "summary")
        return { kind: "summary", summary: value as unknown as SummaryLine };
      if (value._type === "error")
        return { kind: "error", error: value as unknown as ErrorLine };
      return { kind: "json", value };
    } catch {
      // Not valid JSON despite leading brace — treat as opaque data text.
      return { kind: "quad", text: line };
    }
  }

  // N-Quad / N-Triple style data line.
  return { kind: "quad", text: line };
}
