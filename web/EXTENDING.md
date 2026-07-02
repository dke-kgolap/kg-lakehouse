# Extending the KG-OLAP Console

The app is **feature-first**: each screen is a self-contained module under
`src/features/<name>/`, the App Router tree only wires features in, and all
backend access funnels through `src/lib/api` → `/api/*` proxy routes →
the `surface` BFF. The browser never talks to `surface` directly.

## Add a new screen (4 steps, no edits to existing features)

1. **Feature module** — create `src/features/<name>/` with an `index.tsx` that
   exports a single screen component:

   ```tsx
   // src/features/reports/index.tsx
   export function ReportsScreen() {
     return <>…</>;
   }
   ```

2. **Route** — create a thin re-export page:

   ```tsx
   // src/app/(routes)/reports/page.tsx
   export { ReportsScreen as default } from "@/features/reports";
   ```

3. **Nav entry** — append one object to `NAV_ITEMS` in
   `src/components/layout/nav.tsx`. The sidebar renders from this array, so the
   link appears automatically:

   ```ts
   { href: "/reports", label: "Reports", icon: FileBarChart,
     description: "…" }
   ```

4. **Backend data (if needed)** — add a typed method in
   `src/lib/api/endpoints.ts` (it calls `/api/...`), a matching response type in
   `src/lib/api/types.ts`, and a Route Handler under `src/app/api/...` using the
   `proxyJson` / `proxyStream` helpers in `src/app/api/_proxy/surface.ts`.

## Why the boundary holds

`endpoints.ts` only ever fetches same-origin `/api/*` paths. The proxy helpers
are the single place that knows `SURFACE_BASE_URL` and injects HTTP Basic
credentials — both are **server-only** env (no `NEXT_PUBLIC_` prefix), so they
never reach the browser bundle. A new feature physically cannot call the
backend directly without going through a Route Handler.

## Key building blocks

| Concern | Location |
| --- | --- |
| Server proxy (auth + streaming) | `src/app/api/_proxy/surface.ts` |
| Typed browser client | `src/lib/api/{client,endpoints,types}.ts` |
| Shared data hooks (TanStack Query) | `src/lib/api/hooks.ts` |
| NDJSON streaming | `src/lib/ndjson/`, `features/query-workspace/hooks/use-query-stream.ts` |
| RDF parse + 3-module split | `src/lib/rdf/` |
| UI primitives | `src/components/ui/` |
