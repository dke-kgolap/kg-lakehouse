"use client";

import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";

const EXAMPLES = [
  "SELECT *",
  "SELECT location_fir=LOVV",
  "SELECT location_fir=LOVV AND topic_category=AirportHeliport",
  "SELECT location_territory=Austria ROLLUP ON time_month, location_fir",
];

export function DslEditor({
  value,
  onChange,
}: {
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="flex flex-col gap-2">
      <Textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="SELECT location_fir=LOVV AND topic_category=AirportHeliport ROLLUP ON time_year"
        className="min-h-24"
        spellCheck={false}
      />
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
          Examples
        </span>
        {EXAMPLES.map((ex) => (
          <button key={ex} type="button" onClick={() => onChange(ex)}>
            <Badge
              variant="outline"
              className="cursor-pointer hover:bg-accent"
            >
              {ex}
            </Badge>
          </button>
        ))}
      </div>
    </div>
  );
}
