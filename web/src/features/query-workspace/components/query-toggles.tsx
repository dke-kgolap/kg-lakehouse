"use client";

import { Select } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { REPRESENTATIONS, type GraphRepresentation } from "@/lib/api/types";

export function QueryToggles({
  representation,
  onRepresentation,
  reasoning,
  onReasoning,
}: {
  representation: GraphRepresentation;
  onRepresentation: (r: GraphRepresentation) => void;
  reasoning: boolean;
  onReasoning: (r: boolean) => void;
}) {
  return (
    <div className="flex items-end gap-4">
      <div className="flex flex-col gap-1">
        <Label htmlFor="representation">Representation</Label>
        <Select
          id="representation"
          value={representation}
          onChange={(e) =>
            onRepresentation(e.target.value as GraphRepresentation)
          }
          className="w-40"
        >
          {REPRESENTATIONS.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </Select>
      </div>
      <label className="flex h-9 items-center gap-2 text-xs">
        <Switch checked={reasoning} onCheckedChange={onReasoning} id="reasoning" />
        Reasoning
      </label>
    </div>
  );
}
