import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn("size-4 animate-spin", className)} />;
}

/** Centered loading / error / empty states for data panels. */
export function PanelState({
  kind,
  message,
}: {
  kind: "loading" | "error" | "empty";
  message?: string;
}) {
  return (
    <div className="flex h-full min-h-32 flex-col items-center justify-center gap-2 p-6 text-sm text-muted-foreground">
      {kind === "loading" && <Spinner className="size-5" />}
      <span className={kind === "error" ? "text-destructive" : undefined}>
        {message ??
          (kind === "loading"
            ? "Loading…"
            : kind === "error"
              ? "Something went wrong."
              : "Nothing to show.")}
      </span>
    </div>
  );
}
