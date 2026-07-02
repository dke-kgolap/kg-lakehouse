package at.jku.dke.bigkgolap.query.api;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.SchemaNotFoundException;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import org.springframework.stereotype.Component;

/**
 * Centralises schema lookup so the controller's mapping is uniform — {@code
 * SchemaNotFoundException} already has a 404 mapping in {@link ErrorAdvice}.
 */
@Component
public class SchemaResolver {

  private final SchemaRepository schemas;

  public SchemaResolver(SchemaRepository schemas) {
    this.schemas = schemas;
  }

  public CubeSchema requireSchema(String schemaId) {
    CubeSchema schema = schemas.get(schemaId);
    if (schema == null) {
      throw new SchemaNotFoundException(schemaId);
    }
    return schema;
  }
}
