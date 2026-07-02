package at.jku.dke.bigkgolap.surface.api;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.surface.api.Exceptions.NotFoundException;
import at.jku.dke.bigkgolap.surface.api.dto.IngestionLogsResponse;
import at.jku.dke.bigkgolap.surface.api.dto.LogsDto;
import at.jku.dke.bigkgolap.surface.api.dto.QueryLogsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schemas/{schemaId}/logs")
public class LogsController {

  private final SchemaRepository schemas;
  private final IndexRepository index;

  public LogsController(SchemaRepository schemas, IndexRepository index) {
    this.schemas = schemas;
    this.index = index;
  }

  @GetMapping("/ingestion")
  public IngestionLogsResponse ingestionLogs(
      @PathVariable String schemaId, @RequestParam(defaultValue = "20") int limit) {
    if (schemas.get(schemaId) == null) {
      throw new NotFoundException("Unknown schema '" + schemaId + "'");
    }
    var entries =
        index.getIngestionLogs(schemaId, limit).stream().map(LogsDto::toIngestionDto).toList();
    return new IngestionLogsResponse(entries);
  }

  @GetMapping("/query")
  public QueryLogsResponse queryLogs(
      @PathVariable String schemaId, @RequestParam(defaultValue = "20") int limit) {
    if (schemas.get(schemaId) == null) {
      throw new NotFoundException("Unknown schema '" + schemaId + "'");
    }
    var entries = index.getQueryLogs(schemaId, limit).stream().map(LogsDto::toQueryDto).toList();
    return new QueryLogsResponse(entries);
  }
}
