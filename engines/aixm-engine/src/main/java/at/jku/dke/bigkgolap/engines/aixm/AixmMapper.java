package at.jku.dke.bigkgolap.engines.aixm;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.Mapper;
import at.jku.dke.bigkgolap.engines.aixm.internal.AixmRdfWriter;
import java.io.InputStream;

public class AixmMapper implements Mapper {

  @Override
  public void map(InputStream input, GraphBuilder builder) {
    new AixmRdfWriter(builder).parse(input);
  }
}
