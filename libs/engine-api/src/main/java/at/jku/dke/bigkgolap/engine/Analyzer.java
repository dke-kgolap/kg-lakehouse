package at.jku.dke.bigkgolap.engine;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.InputStream;

public interface Analyzer {
  AnalyzerResult analyze(InputStream input, CubeSchema schema);
}
