"use client";

import { Select } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { useSchemas } from "@/lib/api/hooks";

export function SchemaPicker({
  value,
  onChange,
}: {
  value: string | null;
  onChange: (schemaId: string) => void;
}) {
  const { data, isLoading, isError } = useSchemas();
  const schemas = data?.schemas ?? [];

  return (
    <div className="flex flex-col gap-1">
      <Label htmlFor="schema-picker">Schema</Label>
      <Select
        id="schema-picker"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        disabled={isLoading || isError}
      >
        <option value="" disabled>
          {isLoading
            ? "Loading schemas…"
            : isError
              ? "Surface unreachable"
              : schemas.length === 0
                ? "No schemas registered"
                : "Select a schema"}
        </option>
        {schemas.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </Select>
    </div>
  );
}
