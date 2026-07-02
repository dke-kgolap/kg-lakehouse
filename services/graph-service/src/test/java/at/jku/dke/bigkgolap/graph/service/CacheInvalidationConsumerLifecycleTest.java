package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.graph.config.LakehouseProperties;
import at.jku.dke.bigkgolap.graph.fakes.InMemoryGraphCache;
import at.jku.dke.bigkgolap.messaging.CacheInvalidationEvent;
import at.jku.dke.bigkgolap.messaging.testing.RecordingMessagingService;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CacheInvalidationConsumerLifecycleTest {

  @Test
  void startSubscribesWithPerReplicaGroupIdAndHandlerDeletesCacheEntries() {
    InMemoryGraphCache cache = new InMemoryGraphCache();
    cache.upsertGraph("atm", "ctx-1", GraphRepresentation.RDF, new byte[] {1});
    cache.upsertGraph("atm", "ctx-2", GraphRepresentation.RDF, new byte[] {2});
    cache.upsertGraph("atm", "ctx-untouched", GraphRepresentation.RDF, new byte[] {3});

    RecordingMessagingService messaging = new RecordingMessagingService();
    LakehouseProperties props = testProperties();

    CacheInvalidationConsumerLifecycle lifecycle =
        new CacheInvalidationConsumerLifecycle(
            messaging,
            cache,
            props,
            new InProcessGraphCache(
                true, 1024, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    lifecycle.start();

    Assertions.assertThat(messaging.invalidationGroupIds).hasSize(1);
    Assertions.assertThat(messaging.invalidationGroupIds.get(0))
        .startsWith("graph-service.cache-invalidation.test-suffix.");

    messaging.deliverInvalidation(
        new CacheInvalidationEvent(
            "atm", List.of("ctx-1", "ctx-2"), List.of("RDF", "LPG", "GRAPH_FRAME"), Instant.now()));

    Assertions.assertThat(cache.loadGraph("atm", "ctx-1", GraphRepresentation.RDF)).isNull();
    Assertions.assertThat(cache.loadGraph("atm", "ctx-2", GraphRepresentation.RDF)).isNull();
    Assertions.assertThat(cache.loadGraph("atm", "ctx-untouched", GraphRepresentation.RDF))
        .isNotNull();
  }

  private LakehouseProperties testProperties() {
    return new LakehouseProperties(
        new LakehouseProperties.Grpc(0),
        new LakehouseProperties.Graph(
            new LakehouseProperties.Graph.Chunk(8), new LakehouseProperties.Graph.L1(true, 4096)),
        new LakehouseProperties.Cache(
            "redis",
            new LakehouseProperties.Cache.Redis("localhost", 6379, 1),
            new LakehouseProperties.Cache.Invalidation("test-suffix")),
        new LakehouseProperties.Schemas("/tmp"),
        new LakehouseProperties.Storage(
            "local",
            new LakehouseProperties.Storage.Local("/tmp"),
            new LakehouseProperties.Storage.Minio("http://x", "b", "k", "s")),
        new LakehouseProperties.Cassandra("localhost", 9042, "dc1", "ks"),
        new LakehouseProperties.Messaging("localhost:9092"));
  }
}
