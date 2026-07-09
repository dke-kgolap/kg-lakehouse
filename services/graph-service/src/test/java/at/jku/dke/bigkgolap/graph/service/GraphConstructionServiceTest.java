package at.jku.dke.bigkgolap.graph.service;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import at.jku.dke.bigkgolap.engine.Engines;
import at.jku.dke.bigkgolap.graph.fakes.InMemoryGraphCache;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.storage.LocalStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphConstructionServiceTest {

  @TempDir Path storageRoot;

  private InMemoryGraphCache cache;
  private InMemoryIndexRepository index;
  private LocalStorageService storage;
  private FileLoaderService loader;
  private SimpleMeterRegistry meterRegistry;
  private ConstructionThrottle throttle;
  private GraphConstructionService construction;

  @BeforeEach
  void setUp() {
    cache = new InMemoryGraphCache();
    index = new InMemoryIndexRepository();
    storage = new LocalStorageService(storageRoot);
    loader = new FileLoaderService(storage, index, Engines.discover());
    meterRegistry = new SimpleMeterRegistry();
    throttle = new ConstructionThrottle(2);
    construction =
        new GraphConstructionService(
            cache,
            loader,
            meterRegistry,
            new InProcessGraphCache(true, 1024, new SimpleMeterRegistry()),
            throttle);
  }

  @Test
  void cacheMissPathLoadsFromStorageAndWritesThroughToCache() {
    seedFixture("ctx-A");

    GraphResult result = construction.buildGraph("atm", "ctx-A", GraphRepresentation.RDF);

    Assertions.assertThat(result.fromCache()).isFalse();
    Assertions.assertThat(result.tripleCount()).isGreaterThan(0);
    Assertions.assertThat(cache.storage)
        .containsKey(new InMemoryGraphCache.Key("atm", "ctx-A", GraphRepresentation.RDF));
    Assertions.assertThat(missCount()).isEqualTo(1.0);
    Assertions.assertThat(hitCount()).isEqualTo(0.0);
  }

  @Test
  void cacheHitPathReturnsCachedBytesWithoutReRunningMapper() {
    seedFixture("ctx-B");
    construction.buildGraph("atm", "ctx-B", GraphRepresentation.RDF);
    Assertions.assertThat(missCount()).isEqualTo(1.0);

    GraphResult secondCall = construction.buildGraph("atm", "ctx-B", GraphRepresentation.RDF);

    Assertions.assertThat(secondCall.fromCache()).isTrue();
    Assertions.assertThat(secondCall.tripleCount()).isGreaterThan(0);
    Assertions.assertThat(missCount()).isEqualTo(1.0);
    Assertions.assertThat(hitCount()).isEqualTo(1.0);
  }

  @Test
  void clearCacheWithContextIdsDeletesOnlyThoseEntries() {
    cache.upsertGraph("atm", "ctx-1", GraphRepresentation.RDF, new byte[] {1});
    cache.upsertGraph("atm", "ctx-2", GraphRepresentation.RDF, new byte[] {2});
    cache.upsertGraph("weather", "ctx-1", GraphRepresentation.RDF, new byte[] {3});

    int cleared = construction.clearCache("atm", java.util.List.of("ctx-1"));

    Assertions.assertThat(cleared).isEqualTo(1);
    Assertions.assertThat(cache.loadGraph("atm", "ctx-1", GraphRepresentation.RDF)).isNull();
    Assertions.assertThat(cache.loadGraph("atm", "ctx-2", GraphRepresentation.RDF)).isNotNull();
    Assertions.assertThat(cache.loadGraph("weather", "ctx-1", GraphRepresentation.RDF)).isNotNull();
  }

  @Test
  void clearCacheWithEmptyContextIdsFlushesEverything() {
    cache.upsertGraph("atm", "ctx-1", GraphRepresentation.RDF, new byte[] {1});
    cache.upsertGraph("weather", "ctx-1", GraphRepresentation.RDF, new byte[] {3});

    int cleared = construction.clearCache("atm", java.util.List.of());

    Assertions.assertThat(cleared).isEqualTo(0);
    Assertions.assertThat(cache.storage).isEmpty();
  }

  @Test
  void cacheHitDoesNotConsumeAConstructionPermit() throws InterruptedException {
    // Prove hits skip the throttle: with BOTH permits held, a cache hit must still complete.
    seedFixture("ctx-hit");
    construction.buildGraph("atm", "ctx-hit", GraphRepresentation.RDF); // miss, populates cache
    throttle.acquire();
    throttle.acquire(); // 0 permits available
    try {
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> {
            GraphResult hit = construction.buildGraph("atm", "ctx-hit", GraphRepresentation.RDF);
            Assertions.assertThat(hit.fromCache()).isTrue();
          });
    } finally {
      throttle.release();
      throttle.release();
    }
  }

  @Test
  void cacheMissBlocksUntilAPermitIsAvailable() throws Exception {
    // Prove misses acquire the throttle: with no permit, a miss build blocks until one is released.
    seedFixture("ctx-miss");
    throttle.acquire();
    throttle.acquire(); // 0 permits available
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<GraphResult> f =
          pool.submit(() -> construction.buildGraph("atm", "ctx-miss", GraphRepresentation.RDF));
      Assertions.assertThat(awaitDone(f, 500)).as("miss build blocked without a permit").isFalse();
      throttle.release(); // free one permit
      GraphResult result = f.get(10, TimeUnit.SECONDS);
      Assertions.assertThat(result.fromCache()).isFalse();
    } finally {
      throttle.release();
      pool.shutdownNow();
    }
  }

  private static boolean awaitDone(Future<?> f, long millis) throws InterruptedException {
    long deadline = System.nanoTime() + millis * 1_000_000L;
    while (System.nanoTime() < deadline) {
      if (f.isDone()) return true;
      Thread.sleep(20);
    }
    return f.isDone();
  }

  private void seedFixture(String contextId) {
    String storedName = "fixture-" + contextId + ".xml";
    try (InputStream is = loadResource("/fixtures/aixm-multi-feature.xml")) {
      storage.store("atm", storedName, is, -1);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    index.upsertFile("atm", contextId, storedName, "aixm");
    index.upsertFileDetails("atm", storedName, "aixm", "multi.xml", 0);
  }

  private InputStream loadResource(String path) {
    InputStream is = getClass().getResourceAsStream(path);
    if (is == null) {
      throw new IllegalStateException("missing test resource " + path);
    }
    return is;
  }

  @Test
  void interruptWhileAcquiringPermitMapsToConstructionExceptionAndRestoresFlag() {
    // A cache-miss build on an already-interrupted thread: acquire() fails fast, the miss path maps
    // the InterruptedException to GraphConstructionException and restores the interrupt flag; no
    // permit is leaked (none was ever acquired).
    seedFixture("ctx-int");
    Thread.currentThread().interrupt();
    try {
      Assertions.assertThatThrownBy(
              () -> construction.buildGraph("atm", "ctx-int", GraphRepresentation.RDF))
          .isInstanceOf(GraphConstructionException.class);
      Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
      Assertions.assertThat(throttle.availablePermits()).isEqualTo(2);
    } finally {
      Thread.interrupted(); // clear the flag so it does not leak to other tests
    }
  }

  private double hitCount() {
    return meterRegistry
        .counter(
            "lakehouse.query.cache.hit", "result", "hit", "schema", "atm", "representation", "RDF")
        .count();
  }

  private double missCount() {
    return meterRegistry
        .counter(
            "lakehouse.query.cache.hit", "result", "miss", "schema", "atm", "representation", "RDF")
        .count();
  }
}
