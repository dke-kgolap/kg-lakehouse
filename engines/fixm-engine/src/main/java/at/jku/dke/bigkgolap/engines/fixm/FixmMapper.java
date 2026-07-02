package at.jku.dke.bigkgolap.engines.fixm;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.Mapper;
import at.jku.dke.bigkgolap.engines.fixm.internal.FixmRdfWriter;
import java.io.InputStream;

public class FixmMapper implements Mapper {

  @Override
  public void map(InputStream input, GraphBuilder builder) {
    new FixmRdfWriter(builder).parse(input);
  }
}
