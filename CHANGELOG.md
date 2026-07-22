# Changelog

All notable changes to this project are recorded here. Versions follow
[Semantic Versioning](https://semver.org/): patch releases carry bug fixes and
internal improvements with no change to external APIs.

## [1.0.4] — 2026-07-21

### Fixed
- **Query-service readiness no longer flaps during long constructions.** The gRPC clients ping
  their channels every 60 seconds, but the graph- and inference-service servers kept Netty's
  five-minute keepalive permission, so each ping burst drew a `too_many_pings` GOAWAY. Every
  GOAWAY flipped the channel-gated Kubernetes readiness of the query service, which removed its
  headless-service DNS record — the gateway then answered with HTTP 500 until the channel
  recovered. The servers now explicitly permit the clients' cadence, and the query service waits
  for its gRPC targets' DNS before starting, so a simultaneous deployment start cannot leave the
  channels stuck.

## [1.0.3] — 2026-07-21

### Fixed
- **Queries now deliver the knowledge of covering contexts to exactly the cells they cover.** The
  index resolution previously recognized only contexts coarser than the scope in every constrained
  dimension. A context finer than the scope in one dimension and coarser in another — a NOTAM filed
  for a flight information region on a single day, queried with an airport-month scope — was dropped
  from the answer entirely, while a context with concrete values in unconstrained dimensions was
  attached to every result cell. The new covering-context resolution accepts every stored context
  comparable with the scope in all dimensions and strictly coarser in at least one, and the query
  service attaches each covering module to the final cells of exactly the source cells it covers —
  the per-cell knowledge propagation of the CKR model. Verified by unit and integration tests and by
  the evaluation's correctness battery, which gained a covering-delivery law for this property.
- **Container images always package the jar of the current version.** The Dockerfiles referenced a
  hardcoded `target/<service>-1.0.0.jar`, so an image rebuilt after a version bump silently
  packaged the stale jar. Images now copy the single jar produced by a clean build, and a lingering
  stale jar fails the build loudly. (The published 1.0.2 images were audited and contain the
  correct release source; the hazard affected rebuilds only.)

## [1.0.2] — 2026-07-06

### Fixed
- **Services no longer crash-loop when they start before Cassandra is ready.** Each service retries the
  Cassandra connection with a bounded wait while building its session, instead of failing startup when
  the ring has not yet formed (for example, right after a cluster reset). This backs up the existing
  wait-for-cassandra init container.

### Changed
- **Query-service readiness now also gates on the inference-service channel.** The Kubernetes readiness
  probe covers the inference gRPC channel alongside Cassandra and the graph channel, so a freshly rolled
  query-service pod is not reported ready before it can serve reasoning (`reasoning=true`) queries.

## [1.0.1] — 2026-07-04

### Fixed
- **On-demand query memory is now bounded for broad queries.** The query service streams the batched
  graph fan-out to the client instead of buffering the whole (amplified) result, and deduplicates
  emitted quads by a 128-bit hash rather than retaining every quad as a full string. A broad
  construction now completes within a commodity heap. Verified on the cluster: the semantic-correctness
  matrix stays 11/11, and the broadest construction (227,039 quads) ran within about 4.5 GiB with no
  out-of-memory.
- **Surface no longer produces malformed IPv6 endpoint URIs.** On dual-stack hosts the gateway
  round-robined to an unbracketed IPv6 address, yielding a hostless URI and an alternating HTTP 400 on
  every other query. IPv6 literals are now bracketed.

### Changed
- **Graph construction concurrency is bounded.** The graph service caps concurrent cache-miss
  constructions with a fair semaphore (`lakehouse.graph.construction.max-concurrent`, default 4), so peak
  memory stays commodity-sized under broad or concurrent load; excess builds queue rather than fail.
- **Container images no longer pre-touch the heap.** Dropped `-XX:+AlwaysPreTouch` from the service
  images so a pod's memory reflects real use instead of its full reserved heap.

## [1.0.0] — 2026-06

- Initial release.
