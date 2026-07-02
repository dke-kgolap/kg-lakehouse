package at.jku.dke.bigkgolap.model.repository;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.SchemaAlreadyRegisteredException;
import java.io.InputStream;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySchemaRepository implements SchemaRepository {

  private final ConcurrentHashMap<String, CubeSchema> schemas = new ConcurrentHashMap<>();

  @Override
  public SortedSet<String> list() {
    return new TreeSet<>(schemas.keySet());
  }

  @Override
  public CubeSchema get(String id) {
    return schemas.get(id);
  }

  @Override
  public void register(CubeSchema schema) {
    CubeSchema existing = schemas.putIfAbsent(schema.id(), schema);
    if (existing != null && !existing.equals(schema)) {
      throw new SchemaAlreadyRegisteredException(schema.id());
    }
  }

  @Override
  public CubeSchema registerYaml(InputStream input) {
    CubeSchema schema = CubeSchema.fromYaml(input);
    register(schema);
    return schema;
  }
}
