package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.engine.Engines;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.storage.LocalStorageService;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLoaderServiceTest {

  private static final long BYTES_FROM_INPUTSTREAM = -1L;

  @TempDir Path storageRoot;

  private InMemoryIndexRepository index;
  private LocalStorageService storage;
  private List<Engine> engines;
  private FileLoaderService loader;

  @BeforeEach
  void setUp() {
    index = new InMemoryIndexRepository();
    storage = new LocalStorageService(storageRoot);
    engines = Engines.discover();
    boolean hasAixm = engines.stream().anyMatch(e -> e.id().equalsIgnoreCase("aixm"));
    if (!hasAixm) {
      throw new IllegalStateException("aixm engine missing from classpath");
    }
    loader = new FileLoaderService(storage, index, engines);
  }

  @Test
  void loadAndBuildProducesGraphResultWithNonZeroTriplesAndThriftBytes() {
    String storedName = "fixture-1.xml";
    try (InputStream is = loadResource("/fixtures/aixm-multi-feature.xml")) {
      storage.store("atm", storedName, is, BYTES_FROM_INPUTSTREAM);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String ctxId = "ctx-test-1";
    index.upsertFile("atm", ctxId, storedName, "aixm");
    index.upsertFileDetails("atm", storedName, "aixm", "multi.xml", 0);

    GraphResult result = loader.loadAndBuild("atm", ctxId, GraphRepresentation.RDF);

    Assertions.assertThat(result.fromCache()).isFalse();
    Assertions.assertThat(result.tripleCount()).isGreaterThan(0);
    Assertions.assertThat(result.serialized()).isNotEmpty();
    Assertions.assertThat(result.representation()).isEqualTo(GraphRepresentation.RDF);
  }

  @Test
  void loadAndBuildThrowsNoFilesForContextExceptionWhenNoFilesMapped() {
    Assertions.assertThatThrownBy(
            () -> loader.loadAndBuild("atm", "ctx-empty", GraphRepresentation.RDF))
        .isInstanceOf(NoFilesForContextException.class)
        .hasMessageContaining("ctx-empty");
  }

  @Test
  void loadAndBuildThrowsWhenEngineIdIsUnknown() {
    String storedName = "fixture-2.xml";
    try (InputStream is = loadResource("/fixtures/aixm-multi-feature.xml")) {
      storage.store("atm", storedName, is, BYTES_FROM_INPUTSTREAM);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String ctxId = "ctx-test-bogus-engine";
    index.upsertFile("atm", ctxId, storedName, "no-such-engine");
    index.upsertFileDetails("atm", storedName, "no-such-engine", "multi.xml", 0);

    Assertions.assertThatThrownBy(() -> loader.loadAndBuild("atm", ctxId, GraphRepresentation.RDF))
        .isInstanceOf(GraphConstructionException.class)
        .hasMessageContaining("no-such-engine");
  }

  private InputStream loadResource(String path) {
    InputStream is = getClass().getResourceAsStream(path);
    if (is == null) {
      throw new IllegalStateException("missing test resource " + path);
    }
    return is;
  }
}
