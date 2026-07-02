package at.jku.dke.bigkgolap.model.repository;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.InputStream;
import java.util.SortedSet;

public interface SchemaRepository {

  SortedSet<String> list();

  CubeSchema get(String id);

  void register(CubeSchema schema);

  CubeSchema registerYaml(InputStream input);
}
