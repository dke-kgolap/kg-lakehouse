package at.jku.dke.bigkgolap.surface.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.jku.dke.bigkgolap.index.IngestionLogEntry;
import at.jku.dke.bigkgolap.index.QueryLogEntry;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.repository.InMemorySchemaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LogsControllerTest {

  private static final String ATM_YAML =
      """
            schema:
              id: atm
              dimensions:
                time:
                  levels:
                    - { name: year, depth: 0, rollup_to: null, rollup_function: null }
                    - { name: day,  depth: 1, rollup_to: year, rollup_function: "builtin:date_to_year" }
            """;

  private MockMvc mvc;
  private final InMemorySchemaRepository schemas = new InMemorySchemaRepository();
  private final InMemoryIndexRepository index = new InMemoryIndexRepository();

  @BeforeEach
  void setUp() throws Exception {
    index.reset();
    mvc =
        MockMvcBuilders.standaloneSetup(new LogsController(schemas, index))
            .setControllerAdvice(new ErrorAdvice())
            .build();
    if (schemas.get("atm") == null) {
      schemas.registerYaml(new java.io.ByteArrayInputStream(ATM_YAML.getBytes()));
    }
  }

  @Test
  void ingestionLogsReturnsEntriesForKnownSchema() throws Exception {
    index.ingestionLog.add(
        new IngestionLogEntry(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "atm",
            "file.xml",
            "aixm",
            10,
            5,
            15,
            3,
            Instant.parse("2026-05-18T10:00:00Z")));

    mvc.perform(get("/api/schemas/atm/logs/ingestion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logs[0].schemaId").value("atm"))
        .andExpect(jsonPath("$.logs[0].engineId").value("aixm"))
        .andExpect(jsonPath("$.logs[0].contextsCount").value(3))
        .andExpect(jsonPath("$.logs[0].totalMs").value(15));
  }

  @Test
  void queryLogsReturnsEntriesForKnownSchema() throws Exception {
    index.queryLog.add(
        new QueryLogEntry(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "atm",
            "SELECT *",
            7,
            32,
            0,
            40,
            3,
            63,
            0,
            0,
            true,
            null,
            Instant.parse("2026-05-18T10:11:00Z")));

    mvc.perform(get("/api/schemas/atm/logs/query"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logs[0].queryText").value("SELECT *"))
        .andExpect(jsonPath("$.logs[0].quadsCount").value(63))
        .andExpect(jsonPath("$.logs[0].success").value(true));
  }

  @Test
  void ingestionLogsReturns404ForUnknownSchema() throws Exception {
    mvc.perform(get("/api/schemas/nope/logs/ingestion")).andExpect(status().isNotFound());
  }

  @Test
  void queryLogsReturns404ForUnknownSchema() throws Exception {
    mvc.perform(get("/api/schemas/nope/logs/query")).andExpect(status().isNotFound());
  }

  @Test
  void limitParamFiltersResults() throws Exception {
    for (int i = 0; i < 5; i++) {
      index.ingestionLog.add(
          new IngestionLogEntry(
              UUID.randomUUID(), "atm", "file" + i + ".xml", "aixm", 1, 1, 2, 1, Instant.now()));
    }

    mvc.perform(get("/api/schemas/atm/logs/ingestion?limit=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logs.length()").value(2));
  }
}
