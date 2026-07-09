package at.jku.dke.bigkgolap.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.grpc.GraphQueryRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryResponse;
import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.grpc.InferRequest;
import at.jku.dke.bigkgolap.grpc.InferResponse;
import at.jku.dke.bigkgolap.grpc.InferenceServiceGrpc;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.MergeLevels;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import at.jku.dke.bigkgolap.model.repository.InMemorySchemaRepository;
import at.jku.dke.bigkgolap.observability.log.QueryLogger;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryOrchestratorTest {

  private CubeSchema schema;
  private InMemorySchemaRepository schemaRepo;
  private InMemoryIndexRepository index;
  private QueryOrchestrator orchestrator;
  private Server server;
  private ManagedChannel channel;

  @BeforeEach
  void setUp() throws Exception {
    schema = CubeSchema.fromYaml(getClass().getResourceAsStream("/fixtures/atm.yaml"));
    schemaRepo = new InMemorySchemaRepository();
    schemaRepo.register(schema);
    index = new InMemoryIndexRepository();

    // Stub graph-service that echoes back the contextId as a quad.
    String name = InProcessServerBuilder.generateName();
    GraphQueryServiceGrpc.GraphQueryServiceImplBase impl =
        new GraphQueryServiceGrpc.GraphQueryServiceImplBase() {
          @Override
          public void queryGraph(
              GraphQueryRequest req, StreamObserver<GraphQueryResponse> observer) {
            String quad =
                "<urn:test> <urn:has> \""
                    + req.getContextId()
                    + "\" <"
                    + req.getGraphUrisList().get(0)
                    + "> .";
            observer.onNext(GraphQueryResponse.newBuilder().addQuads(quad).build());
            observer.onCompleted();
          }

          @Override
          public void queryGraphBatch(
              at.jku.dke.bigkgolap.grpc.GraphQueryBatchRequest req,
              StreamObserver<GraphQueryResponse> observer) {
            for (var c : req.getContextsList()) {
              String quad =
                  "<urn:test> <urn:has> \""
                      + c.getContextId()
                      + "\" <"
                      + c.getGraphUrisList().get(0)
                      + "> .";
              observer.onNext(
                  GraphQueryResponse.newBuilder()
                      .addQuads(quad)
                      .setContextId(c.getContextId())
                      .build());
            }
            observer.onCompleted();
          }
        };
    // Stub inference-service that returns one derived triple for any request.
    InferenceServiceGrpc.InferenceServiceImplBase inferImpl =
        new InferenceServiceGrpc.InferenceServiceImplBase() {
          @Override
          public void infer(InferRequest req, StreamObserver<InferResponse> observer) {
            observer.onNext(
                InferResponse.newBuilder()
                    .addDerivedTriples("<urn:test:s> <urn:test:type> <urn:test:Super> .")
                    .setFromCache(false)
                    .build());
            observer.onCompleted();
          }

          @Override
          public void inferBatch(
              at.jku.dke.bigkgolap.grpc.InferBatchRequest req,
              StreamObserver<InferResponse> observer) {
            for (var c : req.getContextsList()) {
              observer.onNext(
                  InferResponse.newBuilder()
                      .setContextId(c.getContextId())
                      .addDerivedTriples("<urn:test:s> <urn:test:type> <urn:test:Super> .")
                      .setFromCache(false)
                      .build());
            }
            observer.onCompleted();
          }
        };
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(impl)
            .addService(inferImpl)
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    var stub = GraphQueryServiceGrpc.newBlockingStub(channel);
    var inferStub = InferenceServiceGrpc.newBlockingStub(channel);

    var meterRegistry = new SimpleMeterRegistry();
    Tracer tracer = Tracer.NOOP;
    orchestrator =
        new QueryOrchestrator(
            schemaRepo,
            new ContextResolverService(index),
            new MergeAndPropagateService(),
            new GraphQueryServiceClient(stub, 5L),
            new InferenceServiceClient(inferStub, 5L),
            Executors.newFixedThreadPool(4),
            "urn:test:",
            5L,
            1,
            meterRegistry,
            tracer,
            new QueryLogger(index, tracer));
  }

  @AfterEach
  void tearDown() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  @Test
  void executesQueryWithTimingsPopulated() {
    var year = schema.locate("time", "year");
    var ctx =
        Context.of(List.of(HierarchyFactory.create(new Member(year, "2018"), schema)), schema);
    index.upsertContext(schema, ctx);

    ParsedQuery parsed =
        new ParsedQuery(
            SliceDiceContext.empty(), null, GraphRepresentation.RDF, "application/n-quads");
    var response = orchestrator.execute("atm", "test-query", parsed);

    assertThat(response.success()).isTrue();
    assertThat(response.contextCount()).isEqualTo(1);
    assertThat(response.finalContextCount()).isEqualTo(1);
    assertThat(response.quadCount()).isGreaterThan(0);
    assertThat(response.timings().totalMs()).isGreaterThanOrEqualTo(0);
    assertThat(response.data()).contains(ctx.id());
    assertThat(response.data()).contains("urn:test:context/" + ctx.id());
  }

  @Test
  void generalContextsInheritFinalContexts() {
    var year = schema.locate("time", "year");
    var month = schema.locate("time", "month");
    var coarse = Context.of(List.of(Hierarchy.of(new Member(year, "2018"))), schema);
    var fine =
        Context.of(
            List.of(Hierarchy.of(new Member(year, "2018"), new Member(month, "2018-02"))), schema);
    index.upsertContext(schema, coarse);
    index.upsertContext(schema, fine);

    var sliceDice =
        SliceDiceContext.of(
            List.of(HierarchyFactory.create(new Member(month, "2018-02"), schema)), schema);
    ParsedQuery parsed = new ParsedQuery(sliceDice, null);
    var response = orchestrator.execute("atm", "test-query", parsed);

    // specific=fine + general=coarse → contextCount = 2
    assertThat(response.contextCount()).isEqualTo(2);
  }

  @Test
  void emitsCkrModuleMetadataForEachContext() {
    var year = schema.locate("time", "year");
    var ctx =
        Context.of(List.of(HierarchyFactory.create(new Member(year, "2018"), schema)), schema);
    index.upsertContext(schema, ctx);

    ParsedQuery parsed =
        new ParsedQuery(
                SliceDiceContext.empty(), null, GraphRepresentation.RDF, "application/n-quads")
            .withReasoning(true);
    var data = orchestrator.execute("atm", "test-query", parsed).data();

    String g = "urn:test:context/" + ctx.id();
    // Asserted/derived module split metadata, mirroring the reference KG-OLAP structure.
    assertThat(data).contains("hasAssertedModule").contains(g + "-mod");
    assertThat(data).contains("hasModule").contains(g + "-inf");
    assertThat(data).contains("http://dkm.fbk.eu/ckr/meta#closureOf");
    assertThat(data).contains("http://dkm.fbk.eu/ckr/meta#derivedFrom");
  }

  @Test
  void emitsCoverageWhenRollingUp() {
    var year = schema.locate("time", "year");
    var month = schema.locate("time", "month");
    var fine =
        Context.of(
            List.of(Hierarchy.of(new Member(year, "2018"), new Member(month, "2018-02"))), schema);
    index.upsertContext(schema, fine);

    var sliceDice =
        SliceDiceContext.of(
            List.of(HierarchyFactory.create(new Member(month, "2018-02"), schema)), schema);
    // Roll the time dimension up to year: the fine (month) context is covered by the year context.
    ParsedQuery parsed =
        new ParsedQuery(sliceDice, new MergeLevels(java.util.Map.of("time", year)))
            .withReasoning(true);
    var data = orchestrator.execute("atm", "test-query", parsed).data();

    assertThat(data).contains("http://dkm.fbk.eu/ckr/olap-model#covers");
    assertThat(data).contains("context/" + fine.id());
  }

  @Test
  void reasoningOnStampsDerivedTriplesIntoInfModule() {
    var year = schema.locate("time", "year");
    var ctx =
        Context.of(List.of(HierarchyFactory.create(new Member(year, "2018"), schema)), schema);
    index.upsertContext(schema, ctx);

    ParsedQuery parsed =
        new ParsedQuery(
            SliceDiceContext.empty(), null, GraphRepresentation.RDF, "application/n-quads", true);
    var data = orchestrator.execute("atm", "test-query", parsed).data();

    // The stub inference-service's derived triple lands in the context's -inf module graph.
    assertThat(data).contains("urn:test:Super");
    assertThat(data).contains("context/" + ctx.id() + "-inf>");
  }

  @Test
  void reasoningOffEmitsNoDerivedTriples() {
    var year = schema.locate("time", "year");
    var ctx =
        Context.of(List.of(HierarchyFactory.create(new Member(year, "2018"), schema)), schema);
    index.upsertContext(schema, ctx);

    ParsedQuery parsed =
        new ParsedQuery(
            SliceDiceContext.empty(), null, GraphRepresentation.RDF, "application/n-quads", false);
    var data = orchestrator.execute("atm", "test-query", parsed).data();

    assertThat(data).doesNotContain("urn:test:Super");
    assertThat(data).doesNotContain("-inf>");
  }

  @Test
  void reasoningOffBatchesFanOutAndReturnsAllContexts() {
    var year = schema.locate("time", "year");
    for (String y : List.of("2016", "2017", "2018")) {
      var ctx = Context.of(List.of(HierarchyFactory.create(new Member(year, y), schema)), schema);
      index.upsertContext(schema, ctx);
    }
    // batchSize=2 over 3 contexts => 2 batches.
    var batched = orchestratorWithBatchSize(2);

    ParsedQuery parsed =
        new ParsedQuery(
            SliceDiceContext.empty(), null, GraphRepresentation.RDF, "application/n-quads", false);
    var response = batched.execute("atm", "test-query", parsed);

    assertThat(response.success()).isTrue();
    assertThat(response.contextCount()).isEqualTo(3);
    for (String y : List.of("2016", "2017", "2018")) {
      assertThat(response.data()).contains(y);
    }
  }

  @Test
  void reasoningBatchedAcrossContextsStampsEveryContextsInfModule() {
    var year = schema.locate("time", "year");
    for (String y : List.of("2016", "2017", "2018")) {
      var ctx = Context.of(List.of(HierarchyFactory.create(new Member(year, y), schema)), schema);
      index.upsertContext(schema, ctx);
    }
    var batched = orchestratorWithBatchSize(2); // 3 contexts => 2 inference batches

    ParsedQuery parsed =
        new ParsedQuery(
            SliceDiceContext.empty(), null, GraphRepresentation.RDF, "application/n-quads", true);
    var data = batched.execute("atm", "test-query", parsed).data();

    assertThat(data).contains("urn:test:Super"); // derived triples present
    assertThat(data).contains("hasAssertedModule"); // base module metadata
    assertThat(data).contains("http://dkm.fbk.eu/ckr/meta#closureOf"); // derived module metadata
    for (String y : List.of("2016", "2017", "2018")) {
      assertThat(data).contains(y); // every context's base emitted
    }
    long infModules = data.lines().filter(l -> l.contains("-inf>")).count();
    assertThat(infModules).isGreaterThanOrEqualTo(3); // one -inf stamp per context
  }

  @Test
  void emitsNoDuplicateQuadsOnAssertedPath() {
    // Invariant guard: a query that touches a general (coarse) context AND a specific (fine)
    // context must NOT emit any quad twice. On the asserted (reasoning=false) path the output is
    // deduped on the emitted (s,p,o,g) quad, so each unique quad reaches the client once. (The
    // pathological ~16x general-ids overlap is reproduced and verified end-to-end on the real
    // depth-2 corpus via the Fuseki SPARQL oracle, not here.)
    var year = schema.locate("time", "year");
    var month = schema.locate("time", "month");
    var a = Context.of(List.of(Hierarchy.of(new Member(year, "2018"))), schema);
    var b =
        Context.of(
            List.of(Hierarchy.of(new Member(year, "2018"), new Member(month, "2018-02"))), schema);
    index.upsertContext(schema, a);
    index.upsertContext(schema, b);

    var sliceDice =
        SliceDiceContext.of(
            List.of(Hierarchy.of(new Member(year, "2018"), new Member(month, "2018-02"))), schema);
    ParsedQuery parsed =
        new ParsedQuery(sliceDice, null, GraphRepresentation.RDF, "application/n-quads", false);
    var data = orchestrator.execute("atm", "test-query", parsed).data();

    var lines = data.lines().filter(l -> !l.isBlank()).toList();
    assertThat(lines).isNotEmpty();
    assertThat(lines).doesNotHaveDuplicates();

    // Deduped count equals the number of distinct lines (hash dedup preserves exactness at unit
    // scale).
    assertThat(lines).hasSize(new java.util.HashSet<>(lines).size());
  }

  private QueryOrchestrator orchestratorWithBatchSize(int batchSize) {
    var meterRegistry = new SimpleMeterRegistry();
    Tracer tracer = Tracer.NOOP;
    var stub = GraphQueryServiceGrpc.newBlockingStub(channel);
    var inferStub = InferenceServiceGrpc.newBlockingStub(channel);
    return new QueryOrchestrator(
        schemaRepo,
        new ContextResolverService(index),
        new MergeAndPropagateService(),
        new GraphQueryServiceClient(stub, 5L),
        new InferenceServiceClient(inferStub, 5L),
        Executors.newFixedThreadPool(4),
        "urn:test:",
        5L,
        batchSize,
        meterRegistry,
        tracer,
        new QueryLogger(index, tracer));
  }
}
