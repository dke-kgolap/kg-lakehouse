package at.jku.dke.bigkgolap.engines.iwxxm;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.engine.Mapper;
import java.util.Set;

public class IwxxmEngine implements Engine {

  @Override
  public String id() {
    return "iwxxm";
  }

  @Override
  public Set<String> supportedMediaTypes() {
    return Set.of("application/xml+iwxxm");
  }

  @Override
  public Analyzer analyzer() {
    return new IwxxmAnalyzer();
  }

  @Override
  public Mapper mapper() {
    return new IwxxmMapper();
  }

  // Mirrors IwxxmConstants.BASE_URI; the mapper emits rdf:type as BASE_URI + localName and the
  // generated subClassOf TBox (from the topic hierarchy) uses the same base.
  @Override
  public String typeBaseUri() {
    return "http://example.org/bigkgolap/atm/iwxxm#";
  }

  // Both resources live in ../../rulesets/iwxxm/, bundled into this jar's classpath root by the
  // pom.xml Maven <resources> block.
  @Override
  public String ontologyResource() {
    return "iwxxm-tbox.ttl";
  }

  @Override
  public String rulesResource() {
    return "iwxxm-ruleset-domain_range.rules";
  }
}
