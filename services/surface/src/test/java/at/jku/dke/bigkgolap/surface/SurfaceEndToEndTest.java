package at.jku.dke.bigkgolap.surface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import at.jku.dke.bigkgolap.messaging.testing.RecordingMessagingService;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.surface.fakes.TestProfileConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestProfileConfig.class)
class SurfaceEndToEndTest {

  private static final String ATM_YAML =
      """
            schema:
              id: atm
              dimensions:
                time:
                  levels:
                    - { name: year,  depth: 0, rollup_to: null,  rollup_function: null }
                    - { name: month, depth: 1, rollup_to: year,  rollup_function: "builtin:date_to_year" }
                    - { name: day,   depth: 2, rollup_to: month, rollup_function: "builtin:date_to_month" }
                location:
                  levels:
                    - { name: territory, depth: 0, rollup_to: null,      rollup_function: null }
                    - { name: fir,       depth: 1, rollup_to: territory, rollup_function: "lookup" }
                    - { name: location,  depth: 2, rollup_to: fir,       rollup_function: "lookup" }
                topic:
                  levels:
                    - { name: category, depth: 0, rollup_to: null,     rollup_function: null }
                    - { name: family,   depth: 1, rollup_to: category, rollup_function: "lookup" }
                    - { name: feature,  depth: 2, rollup_to: family,   rollup_function: "lookup" }
              hierarchies:
                location:
                  - { territory: Austria, fir: LOVV, location: LOWW }
                topic:
                  - { category: AirportHeliport, family: Apron, feature: AircraftStand }
            """;

  @Autowired MockMvc mockMvc;

  @Autowired SchemaRepository schemas;

  @Autowired IndexRepository index;

  @Autowired MessagingService messaging;

  @BeforeEach
  void reset() {
    ((InMemoryIndexRepository) index).reset();
    ((RecordingMessagingService) messaging).publishedTasks.clear();
  }

  @Test
  void healthEndpointIsReachableWithoutAuth() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void unauthenticatedWriteIsRejected() throws Exception {
    mockMvc.perform(get("/api/schemas")).andExpect(status().isUnauthorized());
  }

  @Test
  void registerSchemaReturns201WithLocationHeader() throws Exception {
    mockMvc
        .perform(
            post("/api/schemas")
                .with(basicAuth())
                .contentType("application/x-yaml")
                .content(ATM_YAML))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/schemas/atm"))
        .andExpect(jsonPath("$.id").value("atm"))
        .andExpect(jsonPath("$.dimensions.length()").value(3));

    assertThat(schemas.list()).contains("atm");
  }

  @Test
  void registerInvalidYamlReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/schemas")
                .with(basicAuth())
                .contentType("application/x-yaml")
                .content("not: a: schema"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSchemaReturns404ForUnknownId() throws Exception {
    mockMvc
        .perform(get("/api/schemas/does-not-exist").with(basicAuth()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void listSchemasReturnsRegisteredIds() throws Exception {
    registerAtm();
    mockMvc
        .perform(get("/api/schemas").with(basicAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemas").isArray())
        .andExpect(jsonPath("$.schemas[?(@ == 'atm')]").exists());
  }

  @Test
  void ingestPublishesATaskAndStoresAFile() throws Exception {
    registerAtm();
    var recording = (RecordingMessagingService) messaging;

    var file =
        new MockMultipartFile(
            "file", "sample.xml", "application/xml+aixm", "<message:AIXMBasicMessage/>".getBytes());
    mockMvc
        .perform(multipart("/api/schemas/atm/ingest").file(file).with(basicAuth()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.schemaId").value("atm"))
        .andExpect(jsonPath("$.engineId").value("aixm"))
        .andExpect(jsonPath("$.originalName").value("sample.xml"));

    assertThat(recording.publishedTasks).hasSize(1);
    var task = recording.publishedTasks.get(0);
    assertThat(task.schemaId()).isEqualTo("atm");
    assertThat(task.engineId()).isEqualTo("aixm");
    assertThat(task.storedName()).endsWith("_sample.xml");
  }

  @Test
  void ingestWithUnknownSchemaReturns404() throws Exception {
    var file =
        new MockMultipartFile("file", "sample.xml", "application/xml+aixm", "<x/>".getBytes());
    mockMvc
        .perform(multipart("/api/schemas/unknown/ingest").file(file).with(basicAuth()))
        .andExpect(status().isNotFound());
  }

  @Test
  void ingestWithUnsupportedMediaTypeReturns415() throws Exception {
    registerAtm();
    var file = new MockMultipartFile("file", "sample.csv", "text/csv", "a,b,c".getBytes());
    mockMvc
        .perform(multipart("/api/schemas/atm/ingest").file(file).with(basicAuth()))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void statsEndpointReturnsProjectionOfIndexStats() throws Exception {
    registerAtm();
    mockMvc
        .perform(get("/api/schemas/atm/stats").with(basicAuth()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.schemaId").value("atm"))
        .andExpect(jsonPath("$.totalContexts").value(0));
  }

  @Test
  void statsEndpointReturns404ForUnknownSchema() throws Exception {
    mockMvc
        .perform(get("/api/schemas/missing/stats").with(basicAuth()))
        .andExpect(status().isNotFound());
  }

  private void registerAtm() throws Exception {
    if (schemas.get("atm") != null) return;
    mockMvc
        .perform(
            post("/api/schemas")
                .with(basicAuth())
                .contentType("application/x-yaml")
                .content(ATM_YAML))
        .andExpect(status().isCreated());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor basicAuth() {
    return SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin");
  }
}
