"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Copy, Download, Check } from "lucide-react";
import type { QueryStreamState } from "../../hooks/use-query-stream";

export function RawView({ state }: { state: QueryStreamState }) {
  const [copied, setCopied] = useState(false);

  const lines = [...state.rawLines];
  if (state.summary) lines.push(JSON.stringify(state.summary));
  const text = lines.join("\n");

  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const download = () => {
    const blob = new Blob([text], { type: "application/x-ndjson" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "query-result.ndjson";
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-end gap-2 border-b border-border px-3 py-1.5">
        <Button variant="outline" size="sm" onClick={copy} disabled={!text}>
          {copied ? <Check /> : <Copy />}
          {copied ? "Copied" : "Copy"}
        </Button>
        <Button variant="outline" size="sm" onClick={download} disabled={!text}>
          <Download />
          Download
        </Button>
      </div>
      <pre className="flex-1 overflow-auto p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap break-all">
        {text || (
          <span className="text-muted-foreground">No output.</span>
        )}
      </pre>
    </div>
  );
}
