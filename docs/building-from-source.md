# Building from source

The deployment guides use the published container images
(`basharahmad/lakehouse-*:1.0.4`), so **you do not need to build anything to run
the system**. This document is for readers who want to build the artifacts
themselves — the service JAR files, the web console, and, if they wish, their own
container images.

## Requirements

| Requirement | Version | Needed for |
| --- | --- | --- |
| Java Development Kit (JDK) | 21 | The five backend services |
| Maven | 3.9 or later | The backend build |
| Node.js + npm | Node 22 | The web console |
| Docker | any recent | Building container images (optional) |

## Build the backend services

From the repository root:

```sh
mvn package
```

This compiles every module, runs the unit tests, and writes one self-contained
(runnable) JAR per service:

```
services/surface/target/surface-1.0.4.jar
services/ingestion-service/target/ingestion-service-1.0.4.jar
services/query-service/target/query-service-1.0.4.jar
services/graph-service/target/graph-service-1.0.4.jar
services/inference-service/target/inference-service-1.0.4.jar
```

Useful variants:

```sh
mvn package -DskipTests          # skip the tests for a faster build
mvn verify                       # package + format check + integration tests (needs Docker)
mvn -pl libs/domain-model test   # test a single module
```

## Build the web console

The console is a separate Node.js project under `web/`:

```sh
cd web
npm ci          # install dependencies from the lockfile
npm run build    # produce the production build
npm start        # serve it on http://localhost:3001
```

## Build your own container images

Each service ships a `Dockerfile`. After `mvn package` has produced the JAR
files, build one image per service (tag them for any registry you control):

```sh
for svc in surface ingestion-service query-service graph-service inference-service; do
  docker build -t <your-registry>/lakehouse-$svc:<tag> -f services/$svc/Dockerfile services/$svc
done
```

The web console builds directly from its own multi-stage `Dockerfile` (no prior
Maven step needed):

```sh
docker build -t <your-registry>/lakehouse-web:<tag> -f web/Dockerfile web
```

Push the images to your registry, then point the deployment manifests or the
Compose file at them in place of the published `basharahmad/lakehouse-*` images.
See [deployment-kubernetes.md](deployment-kubernetes.md) and
[deployment-docker.md](deployment-docker.md).
