package at.jku.dke.bigkgolap.graph.grpc;

import at.jku.dke.bigkgolap.engine.Engines;
import at.jku.dke.bigkgolap.graph.fakes.InMemoryGraphCache;
import at.jku.dke.bigkgolap.graph.service.ConstructionThrottle;
import at.jku.dke.bigkgolap.graph.service.FileLoaderService;
import at.jku.dke.bigkgolap.graph.service.GraphConstructionService;
import at.jku.dke.bigkgolap.graph.service.InProcessGraphCache;
import at.jku.dke.bigkgolap.grpc.GraphCacheRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.storage.LocalStorageService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphQueryServiceImplTest {

  private static final int SMALL_CHUNK = 8; // exercise multi-chunk streaming path

  @TempDir Path storageRoot;

  private InMemoryGraphCache cache;
  private InProcessGraphCache l1;
  private InMemoryIndexRepository index;
  private LocalStorageService storage;
  private Server server;
  private ManagedChannel channel;
  private GraphQueryServiceGrpc.GraphQueryServiceBlockingStub stub;
  private final String serverName = "graph-test-" + UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    cache = new InMemoryGraphCache();
    index = new InMemoryIndexRepository();
    storage = new LocalStorageService(storageRoot);
    var engines = Engines.discover();
    var loader = new FileLoaderService(storage, index, engines);
    l1 = new InProcessGraphCache(true, 1024, new SimpleMeterRegistry());
    var construction =
        new GraphConstructionService(
            cache, loader, new SimpleMeterRegistry(), l1, new ConstructionThrottle(4));
    var impl = new GraphQueryServiceImpl(construction, new SimpleMeterRegistry(), SMALL_CHUNK);
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(impl)
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    stub = GraphQueryServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void tearDown() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  @Test
  void queryGraphStreamsQuadsOnTheHappyPath() {
    seedFixture("ctx-stream");

    List<at.jku.dke.bigkgolap.grpc.GraphQueryResponse> responses = new ArrayList<>();
    Iterator<at.jku.dke.bigkgolap.grpc.GraphQueryResponse> it =
        stub.queryGraph(
            GraphQueryRequest.newBuilder()
                .setSchemaId("atm")
                .setContextId("ctx-stream")
                .addGraphUris("urn:graph:atm:ctx-stream")
                .setRepresentation("RDF")
                .build());
    it.forEachRemaining(responses::add);

    long totalQuads = responses.stream().mapToLong(r -> r.getQuadsCount()).sum();
    Assertions.assertThat(totalQuads).isGreaterThan(0);
    // Asserted facts are stamped into the context's "-mod" (asserted module) graph.
    String firstQuad =
        responses.stream().filter(r -> r.getQuadsCount() > 0).findFirst().orElseThrow().getQuads(0);
    Assertions.assertThat(firstQuad).contains("<urn:graph:atm:ctx-stream-mod>").endsWith(" .");
  }

  @Test
  void queryGraphReturnsNotFoundWhenNoFilesMappedToContext() {
    Assertions.assertThatThrownBy(
            () -> {
              Iterator<at.jku.dke.bigkgolap.grpc.GraphQueryResponse> it =
                  stub.queryGraph(
                      GraphQueryRequest.newBuilder()
                          .setSchemaId("atm")
                          .setContextId("nonexistent")
                          .addGraphUris("urn:graph:atm:nonexistent")
                          .setRepresentation("RDF")
                          .build());
              it.forEachRemaining(r -> {});
            })
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex -> Assertions.assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
  }

  @Test
  void queryGraphReturnsInvalidArgumentWhenGraphUrisIsEmpty() {
    Assertions.assertThatThrownBy(
            () -> {
              Iterator<at.jku.dke.bigkgolap.grpc.GraphQueryResponse> it =
                  stub.queryGraph(
                      GraphQueryRequest.newBuilder()
                          .setSchemaId("atm")
                          .setContextId("anything")
                          .setRepresentation("RDF")
                          .build());
              it.forEachRemaining(r -> {});
            })
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex ->
                Assertions.assertThat(ex.getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT));
  }

  @Test
  void queryGraphReturnsGraphSonElementsForLpgRepresentation() {
    seedFixture("ctx-lpg");

    List<at.jku.dke.bigkgolap.grpc.GraphQueryResponse> responses = new ArrayList<>();
    stub.queryGraph(
            GraphQueryRequest.newBuilder()
                .setSchemaId("atm")
                .setContextId("ctx-lpg")
                .addGraphUris("urn:graph:atm:ctx-lpg")
                .setRepresentation("LPG")
                .build())
        .forEachRemaining(responses::add);

    // LPG flows through the `elements` proto field, not `quads`.
    List<String> elements = new ArrayList<>();
    for (var r : responses) elements.addAll(r.getElementsList());
    Assertions.assertThat(elements).isNotEmpty();
    List<String> quads = new ArrayList<>();
    for (var r : responses) quads.addAll(r.getQuadsList());
    Assertions.assertThat(quads).isEmpty();
    // GraphSON v3 lines start with a JSON object brace.
    Assertions.assertThat(elements.get(0)).startsWith("{");
  }

  @Test
  void secondQueryGraphCallServesFromCache() {
    seedFixture("ctx-cache");

    for (int i = 0; i < 2; i++) {
      stub.queryGraph(
              GraphQueryRequest.newBuilder()
                  .setSchemaId("atm")
                  .setContextId("ctx-cache")
                  .addGraphUris("urn:graph:atm:ctx-cache")
                  .setRepresentation("RDF")
                  .build())
          .forEachRemaining(r -> {});
    }

    Assertions.assertThat(cache.storage)
        .containsKey(new InMemoryGraphCache.Key("atm", "ctx-cache", GraphRepresentation.RDF));
  }

  @Test
  void clearCacheReturnsTheClearedCount() {
    cache.upsertGraph("atm", "ctx-c1", GraphRepresentation.RDF, new byte[] {1});
    cache.upsertGraph("atm", "ctx-c2", GraphRepresentation.RDF, new byte[] {2});

    var response =
        stub.clearCache(
            GraphCacheRequest.newBuilder()
                .setSchemaId("atm")
                .addContextIds("ctx-c1")
                .addContextIds("ctx-c2")
                .build());

    Assertions.assertThat(response.getClearedCount()).isEqualTo(2);
    Assertions.assertThat(cache.loadGraph("atm", "ctx-c1", GraphRepresentation.RDF)).isNull();
    Assertions.assertThat(cache.loadGraph("atm", "ctx-c2", GraphRepresentation.RDF)).isNull();
  }

  @Test
  void queryGraphBatchReturnsUnionOfPerContextQuadsTaggedByContext() {
    seedFixture("ctx-b1");
    seedFixture("ctx-b2");

    java.util.List<at.jku.dke.bigkgolap.grpc.GraphQueryResponse> responses = new ArrayList<>();
    stub.queryGraphBatch(
            at.jku.dke.bigkgolap.grpc.GraphQueryBatchRequest.newBuilder()
                .setSchemaId("atm")
                .setRepresentation("RDF")
                .addContexts(
                    at.jku.dke.bigkgolap.grpc.ContextQuery.newBuilder()
                        .setContextId("ctx-b1")
                        .addGraphUris("urn:graph:atm:ctx-b1"))
                .addContexts(
                    at.jku.dke.bigkgolap.grpc.ContextQuery.newBuilder()
                        .setContextId("ctx-b2")
                        .addGraphUris("urn:graph:atm:ctx-b2"))
                .build())
        .forEachRemaining(responses::add);

    java.util.Set<String> taggedContexts = new java.util.HashSet<>();
    long totalQuads = 0;
    for (var r : responses) {
      if (r.getQuadsCount() > 0) {
        taggedContexts.add(r.getContextId());
        totalQuads += r.getQuadsCount();
      }
    }
    Assertions.assertThat(totalQuads).isGreaterThan(0);
    Assertions.assertThat(taggedContexts).containsExactlyInAnyOrder("ctx-b1", "ctx-b2");
    Assertions.assertThat(
            responses.stream()
                .filter(r -> r.getContextId().equals("ctx-b1") && r.getQuadsCount() > 0)
                .flatMap(r -> r.getQuadsList().stream()))
        .allMatch(q -> q.contains("<urn:graph:atm:ctx-b1-mod>"));
  }

  @Test
  void queryGraphBatchSignalsFromCachePerContextOnSecondCall() {
    seedFixture("ctx-bc");

    var req =
        at.jku.dke.bigkgolap.grpc.GraphQueryBatchRequest.newBuilder()
            .setSchemaId("atm")
            .setRepresentation("RDF")
            .addContexts(
                at.jku.dke.bigkgolap.grpc.ContextQuery.newBuilder()
                    .setContextId("ctx-bc")
                    .addGraphUris("urn:graph:atm:ctx-bc"))
            .build();
    stub.queryGraphBatch(req).forEachRemaining(r -> {});

    boolean anyFromCache = false;
    var it = stub.queryGraphBatch(req);
    while (it.hasNext()) {
      if (it.next().getFromCache()) anyFromCache = true;
    }
    Assertions.assertThat(anyFromCache).isTrue();
  }

  @Test
  void queryGraphBatchRejectsEmptyContexts() {
    Assertions.assertThatThrownBy(
            () ->
                stub.queryGraphBatch(
                        at.jku.dke.bigkgolap.grpc.GraphQueryBatchRequest.newBuilder()
                            .setSchemaId("atm")
                            .setRepresentation("RDF")
                            .build())
                    .forEachRemaining(r -> {}))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            ex ->
                Assertions.assertThat(ex.getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT));
  }

  @Test
  void secondQueryServesByteIdenticalQuadsFromL1() {
    seedFixture("ctx-l1");

    java.util.function.Supplier<java.util.List<String>> run =
        () -> {
          java.util.List<String> quads = new ArrayList<>();
          stub.queryGraph(
                  GraphQueryRequest.newBuilder()
                      .setSchemaId("atm")
                      .setContextId("ctx-l1")
                      .addGraphUris("urn:graph:atm:ctx-l1")
                      .setRepresentation("RDF")
                      .build())
              .forEachRemaining(r -> quads.addAll(r.getQuadsList()));
          return quads;
        };

    var first = run.get(); // L1 miss -> build/L2 -> promote
    var second = run.get(); // L1 hit
    Assertions.assertThat(second).isNotEmpty().isEqualTo(first);
  }

  private void seedFixture(String contextId) {
    String storedName = "fixture-" + contextId + ".xml";
    try (var is = loadResource("/fixtures/aixm-multi-feature.xml")) {
      storage.store("atm", storedName, is, -1);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    index.upsertFile("atm", contextId, storedName, "aixm");
    index.upsertFileDetails("atm", storedName, "aixm", "multi.xml", 0);
  }

  private java.io.InputStream loadResource(String path) {
    java.io.InputStream is = getClass().getResourceAsStream(path);
    if (is == null) {
      throw new IllegalStateException("missing test resource " + path);
    }
    return is;
  }
}
