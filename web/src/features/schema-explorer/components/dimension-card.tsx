"use client";

import { ChevronDown } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { SchemaDimension } from "@/lib/api/types";

export function DimensionCard({ dimension }: { dimension: SchemaDimension }) {
  const levels = [...dimension.levels].sort((a, b) => a.depth - b.depth);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          {dimension.name}
          <Badge variant="secondary">{levels.length} levels</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ol className="flex flex-col">
          {levels.map((level, i) => (
            <li key={level.name} className="flex flex-col">
              <div className="flex items-center gap-2 rounded-md border border-border bg-background px-3 py-2">
                <span className="font-mono text-xs text-muted-foreground">
                  L{level.depth}
                </span>
                <span className="text-sm font-medium">{level.name}</span>
                {level.rollupFunction && (
                  <Badge variant="outline" className="ml-auto">
                    {level.rollupFunction}
                  </Badge>
                )}
              </div>
              {i < levels.length - 1 && (
                <div className="flex justify-center py-0.5 text-muted-foreground">
                  <ChevronDown className="size-3.5" />
                </div>
              )}
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}
