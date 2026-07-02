package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.engine.Engines;
import at.jku.dke.bigkgolap.graph.fakes.InMemoryGraphCache;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.storage.LocalStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.file.Path;
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
  private GraphConstructionService construction;

  @BeforeEach
  void setUp() {
    cache = new InMemoryGraphCache();
    index = new InMemoryIndexRepository();
    storage = new LocalStorageService(storageRoot);
    loader = new FileLoaderService(storage, index, Engines.discover());
    meterRegistry = new SimpleMeterRegistry();
    construction =
        new GraphConstructionService(
            cache,
            loader,
            meterRegistry,
            new InProcessGraphCache(true, 1024, new SimpleMeterRegistry()));
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
