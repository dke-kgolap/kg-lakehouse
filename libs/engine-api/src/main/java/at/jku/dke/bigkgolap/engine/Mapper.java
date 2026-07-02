package at.jku.dke.bigkgolap.engine;

import java.io.InputStream;

public interface Mapper {
  void map(InputStream input, GraphBuilder builder);
}
