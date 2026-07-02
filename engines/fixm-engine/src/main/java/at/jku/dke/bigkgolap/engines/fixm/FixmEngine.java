package at.jku.dke.bigkgolap.engines.fixm;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.engine.Mapper;
import java.util.Set;

public class FixmEngine implements Engine {

  @Override
  public String id() {
    return "fixm";
  }

  @Override
  public Set<String> supportedMediaTypes() {
    return Set.of("application/xml+fixm");
  }

  @Override
  public Analyzer analyzer() {
    return new FixmAnalyzer();
  }

  @Override
  public Mapper mapper() {
    return new FixmMapper();
  }

  // Mirrors FixmConstants.BASE_URI; the mapper emits rdf:type as BASE_URI + localName and the
  // generated subClassOf TBox (from the topic hierarchy) uses the same base.
  @Override
  public String typeBaseUri() {
    return "http://example.org/bigkgolap/atm/fixm#";
  }

  // Both resources live in ../../rulesets/fixm/, bundled into this jar's classpath root by the
  // pom.xml Maven <resources> block.
  @Override
  public String ontologyResource() {
    return "fixm-tbox.ttl";
  }

  @Override
  public String rulesResource() {
    return "fixm-ruleset-domain_range.rules";
  }
}
