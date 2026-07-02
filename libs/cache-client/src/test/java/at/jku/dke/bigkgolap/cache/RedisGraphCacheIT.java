package at.jku.dke.bigkgolap.cache;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPooled;

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisGraphCacheIT {

  private static final int REDIS_PORT = 6379;
  private static final int DEAD_PORT = 6390;
  private static final GraphRepresentation REPR = GraphRepresentation.RDF;

  private final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

  private JedisPooled pool;
  private RedisGraphCache cache;

  @BeforeAll
  void start() {
    Assumptions.assumeTrue(
        isDockerAvailable(), "Docker daemon not reachable — skipping Redis integration tests");
    redis.start();
    pool = new JedisPooled(redis.getHost(), redis.getMappedPort(REDIS_PORT));
    cache = new RedisGraphCache(pool, Duration.ofMinutes(1));
  }

  @AfterAll
  void stop() {
    if (pool != null) {
      pool.close();
    }
    if (redis.isRunning()) {
      redis.stop();
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  @Test
  void upsertThenLoadReturnsTheSameBytes() {
    byte[] payload = new byte[] {0x01, 0x02, 0x03, 0x04};
    cache.upsertGraph("atm", "ctx-roundtrip", REPR, payload);

    CachedGraph loaded = cache.loadGraph("atm", "ctx-roundtrip", REPR);

    assertThat(loaded).isNotNull();
    assertThat(loaded.data()).isEqualTo(payload);
  }

  @Test
  void loadOnMissingKeyReturnsNull() {
    assertThat(cache.loadGraph("atm", "ctx-does-not-exist", REPR)).isNull();
  }

  @Test
  void deleteCachedGraphsRemovesGivenKeys() {
    cache.upsertGraph("atm", "ctx-del-1", REPR, new byte[] {1});
    cache.upsertGraph("atm", "ctx-del-2", REPR, new byte[] {2});
    cache.upsertGraph("atm", "ctx-keep", REPR, new byte[] {3});

    cache.deleteCachedGraphs("atm", List.of("ctx-del-1", "ctx-del-2"), List.of(REPR));

    assertThat(cache.loadGraph("atm", "ctx-del-1", REPR)).isNull();
    assertThat(cache.loadGraph("atm", "ctx-del-2", REPR)).isNull();
    assertThat(cache.loadGraph("atm", "ctx-keep", REPR)).isNotNull();
  }

  @Test
  void deleteCachedGraphsWithEmptyCollectionIsNoOp() {
    cache.upsertGraph("atm", "ctx-survive", REPR, new byte[] {9});

    cache.deleteCachedGraphs("atm", List.of(), List.of(REPR));

    assertThat(cache.loadGraph("atm", "ctx-survive", REPR)).isNotNull();
  }

  @Test
  void keysAreScopedPerSchema() {
    cache.upsertGraph("atm", "shared-id", REPR, new byte[] {0x10});
    cache.upsertGraph("weather", "shared-id", REPR, new byte[] {0x20});

    assertThat(cache.loadGraph("atm", "shared-id", REPR).data()).isEqualTo(new byte[] {0x10});
    assertThat(cache.loadGraph("weather", "shared-id", REPR).data()).isEqualTo(new byte[] {0x20});
  }

  @Test
  void keysAreScopedPerRepresentation() {
    cache.upsertGraph("atm", "ctx-rep", GraphRepresentation.RDF, new byte[] {0xA});
    cache.upsertGraph("atm", "ctx-rep", GraphRepresentation.LPG, new byte[] {0xB});

    assertThat(cache.loadGraph("atm", "ctx-rep", GraphRepresentation.RDF).data())
        .isEqualTo(new byte[] {0xA});
    assertThat(cache.loadGraph("atm", "ctx-rep", GraphRepresentation.LPG).data())
        .isEqualTo(new byte[] {0xB});
  }

  @Test
  void clearFlushesTheDb() {
    cache.upsertGraph("atm", "ctx-flush", REPR, new byte[] {1});
    cache.clear();
    assertThat(cache.loadGraph("atm", "ctx-flush", REPR)).isNull();
  }

  @Test
  void connectionFailuresDegradeGracefullyOnRead() {
    var deadPool = new JedisPooled("127.0.0.1", DEAD_PORT);
    var deadCache = new RedisGraphCache(deadPool, Duration.ofMinutes(1));

    assertThat(deadCache.loadGraph("atm", "ctx-1", REPR)).isNull();
    // upsert/delete must also not throw
    deadCache.upsertGraph("atm", "ctx-1", REPR, new byte[] {1});
    deadCache.deleteCachedGraphs("atm", List.of("ctx-1"), List.of(REPR));
    deadCache.clear();
    deadPool.close();
  }
}
