package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.cache.GraphCache;
import at.jku.dke.bigkgolap.graph.config.LakehouseProperties;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code lakehouse.cache.invalidation} and deletes affected entries from the cache.
 *
 * <p>Each replica gets a unique groupId so all replicas receive every invalidation (fan-out
 * semantics, not work-share). HOSTNAME / POD_NAME provide stable IDs in K8s; UUID is the dev
 * fallback.
 *
 * <p>The event carries a {@code representations} list (enum names) — parse and forward to the cache
 * so each {@code (ctx, repr)} pair becomes a flat {@code DEL}.
 */
@Component
@Profile("!test")
public class CacheInvalidationConsumerLifecycle {

  private static final Logger log =
      LoggerFactory.getLogger(CacheInvalidationConsumerLifecycle.class);

  private final MessagingService messaging;
  private final GraphCache cache;
  private final LakehouseProperties props;
  private final InProcessGraphCache l1;

  public CacheInvalidationConsumerLifecycle(
      MessagingService messaging,
      GraphCache cache,
      LakehouseProperties props,
      InProcessGraphCache l1) {
    this.messaging = messaging;
    this.cache = cache;
    this.props = props;
    this.l1 = l1;
  }

  @PostConstruct
  public void start() {
    String replicaId = System.getenv("HOSTNAME");
    if (replicaId == null) replicaId = System.getenv("POD_NAME");
    if (replicaId == null) replicaId = UUID.randomUUID().toString();

    String groupId =
        "graph-service.cache-invalidation.%s.%s"
            .formatted(props.cache().invalidation().groupIdSuffix(), replicaId);
    log.info("Subscribing to cache invalidations as groupId={}", groupId);

    messaging.consumeCacheInvalidations(
        groupId,
        event -> {
          List<GraphRepresentation> reps = new ArrayList<>();
          for (String name : event.representations()) {
            try {
              reps.add(GraphRepresentation.valueOf(name));
            } catch (IllegalArgumentException e) {
              log.warn(
                  "Ignoring unknown representation '{}' in cache-invalidation" + " event", name);
            }
          }
          cache.deleteCachedGraphs(event.schemaId(), event.contextIds(), reps);
          l1.invalidate(event.schemaId(), event.contextIds(), reps);
          log.debug(
              "Invalidated {} cache entries x {} reps for schema={}",
              event.contextIds().size(),
              reps.size(),
              event.schemaId());
        });
  }
}
