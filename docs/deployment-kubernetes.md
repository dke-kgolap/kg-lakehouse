# Deployment on Kubernetes

This guide deploys the lakehouse to a Kubernetes cluster using the example
manifests under [`deploy/kubernetes`](../deploy/kubernetes) and the published
release images (`basharahmad/lakehouse-*:1.0.0`). The manifests are a clean,
self-contained starting point: one namespace, the supporting infrastructure, the
five backend services, the web console, and an optional observability stack. Adapt
the resource requests, replica counts, and storage to your cluster before any
non-trial use.

## Requirements

| Requirement | Notes |
| --- | --- |
| A Kubernetes cluster, version 1.24 or later | A single node is enough (k3s, kind, minikube, or Docker Desktop) |
| `kubectl`, configured for that cluster | `kubectl get nodes` should list a ready node |
| A default StorageClass | The infrastructure uses `PersistentVolumeClaim`s without naming a class, so the cluster default is used; confirm with `kubectl get storageclass` |
| Cluster capacity | About 6 GB of allocatable memory for the core stack; more with observability |

## Manifest layout

| File | Contents |
| --- | --- |
| `00-namespace.yaml` | The `lakehouse` namespace |
| `01-secrets.example.yaml` | Credentials template — copy and fill in before applying |
| `02-config.yaml` | Shared and per-service configuration |
| `03-schemas.yaml` | The cube schemas, mounted into the services |
| `04-infrastructure.yaml` | Cassandra, Kafka, Redis, and MinIO |
| `05-application.yaml` | The five services and the web console |
| `observability/observability.yaml` | Prometheus, Grafana, Tempo, Loki (optional) |

## Deploy

All commands run from the `deploy/kubernetes` directory.

**1. Create the namespace.**

```sh
kubectl apply -f 00-namespace.yaml
```

**2. Create the secret.** Copy the template, replace every `CHANGE_ME` with a real
value, and apply your copy. Keep the filled-in file out of version control.

```sh
cp 01-secrets.example.yaml secret.yaml
# edit secret.yaml
kubectl apply -f secret.yaml
```

**3. Apply configuration, infrastructure, and services.**

```sh
kubectl apply -f 02-config.yaml -f 03-schemas.yaml
kubectl apply -f 04-infrastructure.yaml
kubectl apply -f 05-application.yaml
```

**4. Wait for everything to become ready.**

```sh
kubectl -n lakehouse rollout status statefulset/cassandra
kubectl -n lakehouse wait --for=condition=available --timeout=300s deployment --all
kubectl -n lakehouse get pods
```

Cassandra is the slowest component to start. Every pod should reach `Running`
with all containers ready.

## Optional: observability

The observability stack (Prometheus, Grafana, Tempo, Loki, and a Promtail log
collector) is applied separately. It includes cluster-scoped roles that let
Prometheus and Promtail discover pods, so it needs a user able to create
`ClusterRole` and `ClusterRoleBinding` objects.

```sh
kubectl apply -f observability/observability.yaml
```

## Access the system

The services are reachable inside the cluster; forward the ports you need to your
workstation:

```sh
kubectl -n lakehouse port-forward svc/surface 8080:8080
kubectl -n lakehouse port-forward svc/web 3001:3001
kubectl -n lakehouse port-forward svc/grafana 3000:3000        # with observability
```

The **web console** is then at <http://localhost:3001>: a browser interface over
the gateway for querying and visualising the knowledge graph (graph, table,
OLAP-cube, and raw views), browsing schemas, ingesting files, and monitoring
health. It proxies to the gateway and prompts for the surface credentials from
your secret.

Then verify the gateway (the health endpoint is open; other endpoints use the
credentials from your secret):

```sh
curl -fsS http://localhost:8080/actuator/health
curl -fsS -u "$LAKEHOUSE_USER:$LAKEHOUSE_PASSWORD" http://localhost:8080/api/schemas/atm
```

For request formats and the query model, see the repository
[README](../README.md) and the [high-level design](high-level-design.md).

## Adapting the example

- **Storage.** The claims use the default StorageClass. To pin a class, add
  `storageClassName` to the `volumeClaimTemplates` in `04-infrastructure.yaml`.
- **Scale.** Cassandra runs as a single node here. Raise its replica count (and
  review the seed configuration) for redundancy.
- **Resources.** The requests and limits target a small cluster; tune them to
  your workload.

## Remove

Deleting the namespace removes the workloads, services, configuration, and data
(the persistent volumes are released):

```sh
kubectl delete namespace lakehouse
```

The observability stack's cluster-scoped roles live outside the namespace and are
deleted separately:

```sh
kubectl delete -f observability/observability.yaml
```
