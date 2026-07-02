package at.jku.dke.bigkgolap.engine.fakes;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.engine.Mapper;
import java.util.Set;

public class FakeEngine implements Engine {

  @Override
  public String id() {
    return "fake";
  }

  @Override
  public Set<String> supportedMediaTypes() {
    return Set.of("application/x-fake");
  }

  @Override
  public Analyzer analyzer() {
    return new FakeAnalyzer();
  }

  @Override
  public Mapper mapper() {
    return new FakeMapper();
  }
}
