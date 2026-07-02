package at.jku.dke.bigkgolap.query.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import at.jku.dke.bigkgolap.grpc.GraphQueryBatchRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryResponse;
import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.repository.InMemorySchemaRepository;
import at.jku.dke.bigkgolap.observability.log.QueryLogger;
import at.jku.dke.bigkgolap.query.api.dto.QueryRequest;
import at.jku.dke.bigkgolap.query.api.dto.StructuredWireRequest;
import at.jku.dke.bigkgolap.query.service.ContextResolverService;
import at.jku.dke.bigkgolap.query.service.GraphQueryServiceClient;
import at.jku.dke.bigkgolap.query.service.MergeAndPropagateService;
import at.jku.dke.bigkgolap.query.service.QueryOrchestrator;
import at.jku.dke.bigkgolap.query.service.QueryParserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QueryControllerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private CubeSchema schema;
  private InMemorySchemaRepository schemaRepo;
  private InMemoryIndexRepository index;
  private Server server;
  private ManagedChannel channel;
  private MockMvc mvc;

  @BeforeEach
  void setUp() throws Exception {
    schema = CubeSchema.fromYaml(getClass().getResourceAsStream("/fixtures/atm.yaml"));
    schemaRepo = new InMemorySchemaRepository();
    schemaRepo.register(schema);
    index = new InMemoryIndexRepository();

    String name = InProcessServerBuilder.generateName();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                new GraphQueryServiceGrpc.GraphQueryServiceImplBase() {
                  @Override
                  public void queryGraph(
                      GraphQueryRequest req, StreamObserver<GraphQueryResponse> observer) {
                    observer.onCompleted();
                  }

                  @Override
                  public void queryGraphBatch(
                      GraphQueryBatchRequest req, StreamObserver<GraphQueryResponse> observer) {
                    observer.onCompleted();
                  }
                })
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    var stub = GraphQueryServiceGrpc.newBlockingStub(channel);

    var meterRegistry = new SimpleMeterRegistry();
    Tracer tracer = Tracer.NOOP;
    var orchestrator =
        new QueryOrchestrator(
            schemaRepo,
            new ContextResolverService(index),
            new MergeAndPropagateService(),
            new GraphQueryServiceClient(stub, 5L),
            null,
            Executors.newFixedThreadPool(2),
            "urn:test:",
            5L,
            1,
            meterRegistry,
            tracer,
            new QueryLogger(index, tracer));
    var controller =
        new QueryController(new QueryParserService(), orchestrator, new SchemaResolver(schemaRepo));

    mvc =
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ErrorAdvice()).build();
  }

  @AfterEach
  void tearDown() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  @Test
  void queryHappyPathReturns200WithNdjsonSummary() throws Exception {
    var resp = stream("/query", new QueryRequest("atm", "SELECT *"));
    assertThat(resp.getStatus()).isEqualTo(200);
    var summary = summaryNode(resp);
    assertThat(summary.get("success").asBoolean()).isTrue();
    assertThat(summary.get("contextCount").asInt()).isEqualTo(0);
    assertThat(summary.get("_type").asText()).isEqualTo("summary");
  }

  @Test
  void queryWithOneStoredContextFansOutAndReturnsMembershipQuads() throws Exception {
    var year = schema.locate("time", "year");
    var ctx =
        Context.of(List.of(HierarchyFactory.create(new Member(year, "2018"), schema)), schema);
    index.upsertContext(schema, ctx);

    var resp = stream("/query", new QueryRequest("atm", "SELECT *"));
    assertThat(resp.getStatus()).isEqualTo(200);
    String[] lines = resp.getContentAsString().strip().split("\n");
    var summary = summaryNode(resp);
    assertThat(summary.get("contextCount").asInt()).isEqualTo(1);
    assertThat(summary.get("quadCount").asLong()).isGreaterThan(0);
    assertThat(lines.length).isGreaterThan(1);
  }

  @Test
  void unknownSchemaReturns404() throws Exception {
    var resp = doPost("/query", new QueryRequest("does-not-exist", "SELECT *"));
    assertThat(resp.getStatus()).isEqualTo(404);
  }

  @Test
  void parserErrorReturns400() throws Exception {
    var resp = doPost("/query", new QueryRequest("atm", "DELETE FROM somewhere"));
    assertThat(resp.getStatus()).isEqualTo(400);
  }

  @Test
  void structuredFormHappyPathReturns200() throws Exception {
    var resp =
        stream(
            "/query/structured",
            new StructuredWireRequest("atm", Map.of("location", Map.of("fir", "LOVV"))));
    assertThat(resp.getStatus()).isEqualTo(200);
    assertThat(summaryNode(resp).get("success").asBoolean()).isTrue();
  }

  // --- helpers ---

  private JsonNode summaryNode(MockHttpServletResponse resp) throws Exception {
    String[] lines = resp.getContentAsString().strip().split("\n");
    return mapper.readTree(lines[lines.length - 1]);
  }

  /** For error responses (4xx) that don't go through async dispatch. */
  private MockHttpServletResponse doPost(String path, Object body) throws Exception {
    return mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andReturn()
        .getResponse();
  }

  /** For streaming 200 responses — waits for the async StreamingResponseBody to complete. */
  private MockHttpServletResponse stream(String path, Object body) throws Exception {
    var asyncResult =
        mvc.perform(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body)))
            .andExpect(request().asyncStarted())
            .andReturn();
    return mvc.perform(asyncDispatch(asyncResult)).andReturn().getResponse();
  }
}
