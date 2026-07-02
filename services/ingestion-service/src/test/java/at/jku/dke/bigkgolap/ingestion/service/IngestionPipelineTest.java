package at.jku.dke.bigkgolap.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.messaging.IngestionTask;
import at.jku.dke.bigkgolap.messaging.testing.SyncMessagingService;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.repository.InMemorySchemaRepository;
import at.jku.dke.bigkgolap.storage.LocalStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IngestionPipelineTest {

  @TempDir Path storageRoot;

  private CubeSchema schema;
  private InMemorySchemaRepository schemas;
  private InMemoryIndexRepository index;
  private SyncMessagingService messaging;
  private LocalStorageService storage;
  private IngestionPipeline pipeline;

  @BeforeEach
  void setUp() {
    schema = CubeSchema.fromYaml(loadResource("/fixtures/atm.yaml"));
    schemas = new InMemorySchemaRepository();
    schemas.register(schema);
    index = new InMemoryIndexRepository();
    messaging = new SyncMessagingService();
    storage = new LocalStorageService(storageRoot);

    var engines = at.jku.dke.bigkgolap.engine.Engines.discover();
    boolean hasAixm = engines.stream().anyMatch(e -> e.id().equals("aixm"));
    if (!hasAixm) {
      throw new IllegalStateException("aixm engine missing from classpath");
    }

    pipeline =
        new IngestionPipeline(
            schemas,
            storage,
            index,
            messaging,
            engines,
            new ContextResolver(),
            new SimpleMeterRegistry());
  }

  @Test
  void multiFeatureAixmFileProducesOneContextPerFeatureDay() throws Exception {
    String storedName = "test-multi.xml";
    try (InputStream in = loadResource("/fixtures/aixm-multi-feature.xml")) {
      storage.store("atm", storedName, in, -1);
    }

    pipeline.process(new IngestionTask("atm", storedName, "aixm", "multi.xml", 0L, Instant.now()));

    // Feature 1 (AircraftStand, LOWW, 2025-01-15) → 1 context
    // Feature 2 (ApronElement, LOWS, 2025-01-16..17) → 2 contexts (day fan-out)
    assertThat(index.contexts.keySet().stream().filter(k -> k.getKey().equals("atm")).toList())
        .hasSize(3);
    assertThat(index.files.stream().filter(f -> f.getKey().equals("atm")).toList()).hasSize(3);
    assertThat(index.ingestionLog).hasSize(1);
    assertThat(index.ingestionLog.get(0).contextsCount()).isEqualTo(3);
    assertThat(messaging.completed).hasSize(1);
    assertThat(messaging.invalidations).hasSize(1);
    assertThat(messaging.invalidations.get(0).contextIds()).hasSize(3);
  }

  @Test
  void unknownSchemaRaisesIngestionExceptionWithoutCommittingSideEffects() throws Exception {
    String storedName = "irrelevant.xml";
    try (InputStream in = loadResource("/fixtures/aixm-multi-feature.xml")) {
      storage.store("atm", storedName, in, -1);
    }

    IngestionTask task =
        new IngestionTask("missing", storedName, "aixm", "multi.xml", 0L, Instant.now());

    assertThatThrownBy(() -> pipeline.process(task))
        .isInstanceOf(IngestionException.class)
        .hasMessageContaining("missing");

    assertThat(index.contexts).isEmpty();
    assertThat(index.ingestionLog).isEmpty();
    assertThat(messaging.completed).isEmpty();
  }

  @Test
  void unknownEngineIdRaisesIngestionException() {
    assertThatThrownBy(
            () ->
                pipeline.process(
                    new IngestionTask("atm", "x", "no-such-engine", "x", 0L, Instant.now())))
        .isInstanceOf(IngestionException.class)
        .hasMessageContaining("no-such-engine");
  }

  private InputStream loadResource(String path) {
    InputStream in = getClass().getResourceAsStream(path);
    if (in == null) {
      throw new IllegalStateException("missing test resource " + path);
    }
    return in;
  }
}
