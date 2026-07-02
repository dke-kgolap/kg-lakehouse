package at.jku.dke.bigkgolap.engines.iwxxm;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.Mapper;
import at.jku.dke.bigkgolap.engines.iwxxm.internal.IwxxmRdfWriter;
import java.io.InputStream;

public class IwxxmMapper implements Mapper {

  @Override
  public void map(InputStream input, GraphBuilder builder) {
    new IwxxmRdfWriter(builder).parse(input);
  }
}
