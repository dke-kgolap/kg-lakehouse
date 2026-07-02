# KG-OLAP Console (`web`)

A Next.js frontend for the lakehouse-big-kgolap framework. It lets users query
the knowledge graph and visualize results (graph / table / OLAP-cube / raw),
browse schemas, monitor query performance, ingest source files, review query
history, and check service health.

It is its own microservice: the browser only ever calls this app's `/api/*`
Route Handlers, which proxy server-side to the `surface` BFF. See
[EXTENDING.md](./EXTENDING.md) for the architecture and how to add screens.

## Authentication

The app passes through surface's HTTP Basic auth. On first load the browser
shows its native login dialog; enter your **surface** username/password (default
`admin` / `admin`). Those credentials are validated against surface and
forwarded by the proxy on every request — the app stores no credentials of its
own. `/api/health` is left open for the container healthcheck.

## Develop

```bash
# 1. Bring up the backend (from the repo root)
docker compose up -d surface ingestion-service inference-service

# 2. Configure + run the frontend
cp .env.local.example .env.local      # SURFACE_BASE_URL, SURFACE_USER, SURFACE_PASSWORD
npm install
npm run dev                            # http://localhost:3001
```

`:3000` is taken by Grafana, so the app runs on `:3001`.

## Build & run (production)

```bash
npm run build && npm start             # standalone server on :3001
# or, as part of the stack:
docker compose up -d --build web
```

## Test

```bash
npm run test:e2e                       # Playwright; needs the stack + ingested data
```

## Configuration (server-only env)

| Variable | Default | Purpose |
| --- | --- | --- |
| `SURFACE_BASE_URL` | `http://localhost:8080` | surface BFF base URL |

Not `NEXT_PUBLIC_*`; read only on the server. User credentials are entered via
the browser login dialog, not configured here.
