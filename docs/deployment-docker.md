# Local deployment with Docker Compose

This guide brings up the complete lakehouse on a single machine using Docker
Compose and the published release images. It runs the five backend services, the
web console, the supporting infrastructure (Cassandra, Kafka, Redis, MinIO), and
the observability stack (Prometheus, Grafana, Tempo, Loki). It is the fastest way
to try the system end to end; for a cluster deployment see
[deployment-kubernetes.md](deployment-kubernetes.md).

## Requirements

| Requirement | Notes |
| --- | --- |
| Docker Engine with the Compose plugin (v2) | `docker compose version` should succeed |
| Memory | About 8 GB free is comfortable for the full stack |
| Free TCP ports | 8080, 3000, 3001, 9000, 9001, 9042, 9092, 9094, 9099, 3100, 3200, 4317, 4318 |

The images are pulled from Docker Hub (`basharahmad/lakehouse-*:1.0.0`); no local
build is required.

## Start the stack

All commands run from the `deploy/docker` directory.

```sh
cd deploy/docker
docker compose up -d --wait
```

The `--wait` flag blocks until every container reports healthy. The first run
pulls the images and initialises Cassandra, so it can take a few minutes.

Check the running containers:

```sh
docker compose ps
```

## Verify

The health endpoint is open (no credentials):

```sh
curl -fsS http://localhost:8080/actuator/health
```

A healthy gateway returns status `UP`. The three cube schemas (`atm`, `fixm`,
`meteo`) are loaded from `config/schemas` at start-up. Confirm one is present
(the API uses HTTP basic authentication; the default local credentials are
`admin` / `admin`):

```sh
curl -fsS -u admin:admin http://localhost:8080/api/schemas/atm
```

## Consoles and endpoints

| Component | URL | Credentials |
| --- | --- | --- |
| REST gateway (surface) | http://localhost:8080 | `admin` / `admin` |
| Web console | http://localhost:3001 | prompts for the surface login (`admin` / `admin`) |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Prometheus | http://localhost:9099 | — |
| MinIO console | http://localhost:9001 | `minioadmin` / `minioadmin` |

The **web console** (port 3001) is a browser interface over the gateway: query
the knowledge graph and view the result as a graph, table, OLAP cube, or raw
data, browse schemas, ingest source files, review query history, and monitor
service health. It proxies to the gateway, so on first load it prompts for the
surface login (`admin` / `admin` by default).

Tracing is enabled in this local stack, so once you exercise the API the traces
appear in Grafana under the Tempo data source, and container logs under Loki.

## Loading and querying data

The stack starts empty. To populate it, upload aeronautical files through the
gateway and then query the reconstructed knowledge graph. See the repository
[README](../README.md) for the request formats, and the
[high-level design](high-level-design.md) for the query model. Sample datasets
can be produced with the accompanying data generator.

## Configuration

Editable configuration ships under `deploy/docker/config`:

- `config/schemas/*.yaml` — the cube schemas (dimensions, levels, roll-up rules).
- `config/monitoring/*` — Prometheus, Loki, Tempo, and Promtail settings.
- `config/grafana/provisioning` — the Grafana data sources.

The default MinIO and application credentials suit local use only. For anything
beyond a local trial, change them in `docker-compose.yaml`.

## Stop and clean up

```sh
docker compose down        # stop the containers, keep the data volumes
docker compose down -v     # stop and delete all data (Cassandra, MinIO, …)
```
