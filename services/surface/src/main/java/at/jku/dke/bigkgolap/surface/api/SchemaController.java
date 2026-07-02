package at.jku.dke.bigkgolap.surface.api;

import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.surface.api.Exceptions.NotFoundException;
import at.jku.dke.bigkgolap.surface.api.dto.SchemaListResponse;
import at.jku.dke.bigkgolap.surface.api.dto.SchemaResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schemas")
public class SchemaController {

  private final SchemaRepository repo;

  public SchemaController(SchemaRepository repo) {
    this.repo = repo;
  }

  @PostMapping(consumes = {"application/x-yaml", "application/yaml", "text/yaml", "text/x-yaml"})
  public ResponseEntity<SchemaResponse> register(HttpServletRequest request) throws Exception {
    var schema = repo.registerYaml(request.getInputStream());
    return ResponseEntity.created(URI.create("/api/schemas/" + schema.id()))
        .body(SchemaResponse.from(schema));
  }

  @GetMapping
  public SchemaListResponse list() {
    return new SchemaListResponse(new java.util.ArrayList<>(repo.list()));
  }

  @GetMapping("/{id}")
  public SchemaResponse get(@PathVariable String id) {
    var schema = repo.get(id);
    if (schema == null) {
      throw new NotFoundException("Unknown schema '" + id + "'");
    }
    return SchemaResponse.from(schema);
  }
}
