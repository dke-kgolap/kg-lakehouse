"use client";

import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Upload, FileUp, CheckCircle2, AlertCircle, X } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { api } from "@/lib/api/endpoints";
import type { SchemaResponse } from "@/lib/api/types";

const PLACEHOLDER = `schema:
  id: my-schema
  dimensions:
    time:
      levels:
        - { name: year, depth: 0, rollup_to: null, rollup_function: null }
        - { name: month, depth: 1, rollup_to: year, rollup_function: "builtin:date_to_year" }
    # …location, topic, etc.`;

export function RegisterSchema({
  onClose,
  onRegistered,
}: {
  onClose: () => void;
  onRegistered: (schemaId: string) => void;
}) {
  const [yaml, setYaml] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const mutation = useMutation<SchemaResponse, Error, string>({
    mutationFn: (text) => api.registerSchema(text),
    onSuccess: (schema) => {
      queryClient.invalidateQueries({ queryKey: ["schemas"] });
      onRegistered(schema.id);
    },
  });

  const onPickFile = async (file: File | undefined) => {
    if (!file) return;
    setYaml(await file.text());
  };

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Register a schema</CardTitle>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close">
          <X />
        </Button>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => fileRef.current?.click()}
          >
            <FileUp /> Load .yaml file
          </Button>
          <span className="text-xs text-muted-foreground">
            or paste the schema definition below
          </span>
          <input
            ref={fileRef}
            type="file"
            accept=".yaml,.yml,text/yaml"
            className="hidden"
            onChange={(e) => onPickFile(e.target.files?.[0])}
          />
        </div>

        <Textarea
          value={yaml}
          onChange={(e) => setYaml(e.target.value)}
          placeholder={PLACEHOLDER}
          className="min-h-56"
          spellCheck={false}
        />

        <div className="flex items-center gap-3">
          <Button
            onClick={() => mutation.mutate(yaml)}
            disabled={!yaml.trim() || mutation.isPending}
          >
            <Upload />
            {mutation.isPending ? "Registering…" : "Register"}
          </Button>
          {mutation.isSuccess && (
            <span className="flex items-center gap-1.5 text-xs text-emerald-500">
              <CheckCircle2 className="size-4" />
              Registered “{mutation.data.id}”.
            </span>
          )}
          {mutation.isError && (
            <span className="flex items-center gap-1.5 text-xs text-destructive">
              <AlertCircle className="size-4" />
              {mutation.error.message}
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
