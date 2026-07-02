"use client";

import { ScreenHeader } from "@/components/layout/app-shell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PanelState } from "@/components/ui/spinner";
import { useHealth } from "@/lib/api/hooks";
import { RefreshCw } from "lucide-react";

function StatusBadge({ status }: { status: string }) {
  const variant =
    status === "UP" ? "success" : status === "DOWN" ? "destructive" : "outline";
  return <Badge variant={variant}>{status}</Badge>;
}

export function HealthScreen() {
  const { data, isLoading, isError, refetch, isFetching } = useHealth();

  return (
    <>
      <ScreenHeader
        title="Health"
        description="Service status"
        actions={
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isFetching}
          >
            <RefreshCw className={isFetching ? "animate-spin" : undefined} />
            Refresh
          </Button>
        }
      />
      <div className="flex-1 overflow-auto p-6">
        {isLoading ? (
          <PanelState kind="loading" />
        ) : isError || !data ? (
          <PanelState kind="error" message="Could not reach the web backend." />
        ) : (
          <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
            <Card>
              <CardHeader className="flex-row items-center justify-between">
                <CardTitle>Web app</CardTitle>
                <StatusBadge status={data.web.status} />
              </CardHeader>
              <CardContent className="text-xs text-muted-foreground">
                Next.js console (this app).
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex-row items-center justify-between">
                <CardTitle>Surface BFF</CardTitle>
                <StatusBadge status={data.surface.status} />
              </CardHeader>
              <CardContent className="text-xs text-muted-foreground">
                {data.surface.detail
                  ? data.surface.detail
                  : "Backend gateway reachable through the server proxy."}
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </>
  );
}
