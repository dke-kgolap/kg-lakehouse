"use client";

import { Select } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Plus, X } from "lucide-react";
import { useSchema } from "@/lib/api/hooks";
import type { PredicateRow, RollupRow } from "../model/query-request";
import { toDslPreview } from "../model/query-request";

export function GuidedBuilder({
  schemaId,
  predicates,
  setPredicates,
  rollups,
  setRollups,
}: {
  schemaId: string | null;
  predicates: PredicateRow[];
  setPredicates: (rows: PredicateRow[]) => void;
  rollups: RollupRow[];
  setRollups: (rows: RollupRow[]) => void;
}) {
  const { data: schema } = useSchema(schemaId);
  const dimensions = schema?.dimensions ?? [];
  const levelsFor = (dim: string) =>
    dimensions.find((d) => d.name === dim)?.levels ?? [];

  const update = <T,>(rows: T[], i: number, patch: Partial<T>): T[] =>
    rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r));

  return (
    <div className="flex flex-col gap-3">
      {/* Predicates */}
      <div className="flex flex-col gap-2">
        <Label>Filters (AND)</Label>
        {predicates.length === 0 && (
          <p className="text-xs text-muted-foreground">
            No filters — matches all contexts (SELECT *).
          </p>
        )}
        {predicates.map((p, i) => (
          <div key={i} className="flex items-center gap-2">
            <Select
              value={p.dimension}
              onChange={(e) =>
                setPredicates(
                  update(predicates, i, { dimension: e.target.value, level: "" }),
                )
              }
              className="w-36"
            >
              <option value="" disabled>
                dimension
              </option>
              {dimensions.map((d) => (
                <option key={d.name} value={d.name}>
                  {d.name}
                </option>
              ))}
            </Select>
            <Select
              value={p.level}
              onChange={(e) =>
                setPredicates(update(predicates, i, { level: e.target.value }))
              }
              className="w-36"
              disabled={!p.dimension}
            >
              <option value="" disabled>
                level
              </option>
              {levelsFor(p.dimension).map((l) => (
                <option key={l.name} value={l.name}>
                  {l.name}
                </option>
              ))}
            </Select>
            <Input
              value={p.value}
              onChange={(e) =>
                setPredicates(update(predicates, i, { value: e.target.value }))
              }
              placeholder="value"
              className="flex-1"
            />
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setPredicates(predicates.filter((_, idx) => idx !== i))}
              aria-label="Remove filter"
            >
              <X />
            </Button>
          </div>
        ))}
        <Button
          variant="outline"
          size="sm"
          className="self-start"
          disabled={!schemaId}
          onClick={() =>
            setPredicates([...predicates, { dimension: "", level: "", value: "" }])
          }
        >
          <Plus />
          Add filter
        </Button>
      </div>

      {/* Rollups */}
      <div className="flex flex-col gap-2">
        <Label>Roll up on</Label>
        {rollups.map((r, i) => (
          <div key={i} className="flex items-center gap-2">
            <Select
              value={r.dimension}
              onChange={(e) =>
                setRollups(
                  update(rollups, i, { dimension: e.target.value, level: "" }),
                )
              }
              className="w-36"
            >
              <option value="" disabled>
                dimension
              </option>
              {dimensions.map((d) => (
                <option key={d.name} value={d.name}>
                  {d.name}
                </option>
              ))}
            </Select>
            <Select
              value={r.level}
              onChange={(e) =>
                setRollups(update(rollups, i, { level: e.target.value }))
              }
              className="w-36"
              disabled={!r.dimension}
            >
              <option value="" disabled>
                level
              </option>
              {levelsFor(r.dimension).map((l) => (
                <option key={l.name} value={l.name}>
                  {l.name}
                </option>
              ))}
            </Select>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setRollups(rollups.filter((_, idx) => idx !== i))}
              aria-label="Remove rollup"
            >
              <X />
            </Button>
          </div>
        ))}
        <Button
          variant="outline"
          size="sm"
          className="self-start"
          disabled={!schemaId}
          onClick={() => setRollups([...rollups, { dimension: "", level: "" }])}
        >
          <Plus />
          Add rollup
        </Button>
      </div>

      <div className="rounded-md bg-muted px-3 py-2 font-mono text-xs text-muted-foreground">
        {toDslPreview(predicates, rollups)}
      </div>
    </div>
  );
}
