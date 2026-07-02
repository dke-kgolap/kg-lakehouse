package at.jku.dke.bigkgolap.surface.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import at.jku.dke.bigkgolap.surface.service.QueryServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class QueryControllerTest {

  private static final String NDJSON = "application/x-ndjson";

  private final ObjectMapper mapper = new ObjectMapper();
  private MockMvc mvc;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    var restClientBuilder = RestClient.builder().baseUrl("http://stub");
    server = MockRestServiceServer.bindTo(restClientBuilder).build();
    var restClient = restClientBuilder.build();
    var controller =
        new QueryController(new QueryServiceClient(restClient), new SimpleMeterRegistry());
    mvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void forwardsSqlQueryAndPipesNdjsonFromQueryService() throws Exception {
    var ndjson = stubNdjson(3, 42L);
    server
        .expect(requestTo("http://stub/query"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().string(Matchers.containsString("\"schemaId\":\"atm\"")))
        .andExpect(content().string(Matchers.containsString("\"query\":\"SELECT *\"")))
        .andRespond(withSuccess(ndjson, MediaType.parseMediaType(NDJSON)));

    var asyncResult =
        mvc.perform(
                post("/api/schemas/atm/query")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("SELECT *"))
            .andExpect(request().asyncStarted())
            .andReturn();
    var resp = mvc.perform(asyncDispatch(asyncResult)).andReturn().getResponse();

    assertThat(resp.getStatus()).isEqualTo(200);
    var summary = summaryNode(resp.getContentAsString());
    assertThat(summary.get("contextCount").asInt()).isEqualTo(3);
    assertThat(summary.get("quadCount").asLong()).isEqualTo(42);
  }

  @Test
  void forwardsStructuredQueryAndPipesNdjson() throws Exception {
    var ndjson = stubNdjson(0, 0L);
    server
        .expect(requestTo("http://stub/query/structured"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(Matchers.containsString("\"schemaId\":\"atm\"")))
        .andRespond(withSuccess(ndjson, MediaType.parseMediaType(NDJSON)));

    var asyncResult =
        mvc.perform(
                post("/api/schemas/atm/query/structured")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"select\":{\"location\":{\"fir\":\"LOVV\"}}}"))
            .andExpect(request().asyncStarted())
            .andReturn();
    var resp = mvc.perform(asyncDispatch(asyncResult)).andReturn().getResponse();

    assertThat(resp.getStatus()).isEqualTo(200);
    assertThat(summaryNode(resp.getContentAsString()).get("success").asBoolean()).isTrue();
  }

  private JsonNode summaryNode(String body) throws Exception {
    var lines = body.trim().lines().toList();
    return mapper.readTree(lines.get(lines.size() - 1));
  }

  private String stubNdjson(int contextCount, long quadCount) {
    var timings =
        "{\"contextResolutionMs\":0,\"mergeMs\":0,\"graphConstructionMs\":0,\"totalMs\":0,\"cacheHits\":0,\"cacheMisses\":0}";
    return "{\"_type\":\"summary\",\"success\":true,\"contextCount\":"
        + contextCount
        + ","
        + "\"finalContextCount\":"
        + contextCount
        + ",\"quadCount\":"
        + quadCount
        + ",\"timings\":"
        + timings
        + ",\"traceId\":null}\n";
  }
}
