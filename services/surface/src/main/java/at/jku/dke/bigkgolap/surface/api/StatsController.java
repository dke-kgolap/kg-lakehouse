package at.jku.dke.bigkgolap.surface.api;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.surface.api.Exceptions.NotFoundException;
import at.jku.dke.bigkgolap.surface.api.dto.StatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schemas/{schemaId}/stats")
public class StatsController {

  private final SchemaRepository schemas;
  private final IndexRepository index;

  public StatsController(SchemaRepository schemas, IndexRepository index) {
    this.schemas = schemas;
    this.index = index;
  }

  @GetMapping
  public StatsResponse stats(@PathVariable String schemaId) {
    if (schemas.get(schemaId) == null) {
      throw new NotFoundException("Unknown schema '" + schemaId + "'");
    }
    return StatsResponse.from(index.getStats(schemaId));
  }
}
