package at.jku.dke.bigkgolap.engines.aixm;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.engine.Mapper;
import java.util.Set;

public class AixmEngine implements Engine {

  @Override
  public String id() {
    return "aixm";
  }

  @Override
  public Set<String> supportedMediaTypes() {
    return Set.of("application/xml+aixm");
  }

  @Override
  public Analyzer analyzer() {
    return new AixmAnalyzer();
  }

  @Override
  public Mapper mapper() {
    return new AixmMapper();
  }

  // Mirrors AixmConstants.BASE_URI (package-private in the internal package). The mapper emits
  // rdf:type objects as BASE_URI + localName, and the generated subClassOf TBox uses the same base.
  @Override
  public String typeBaseUri() {
    return "http://example.org/bigkgolap/atm/aixm#";
  }

  // Both resources live in ../../rulesets/aixm/, bundled into this jar's classpath root by the
  // pom.xml Maven <resources> block. Single canonical home for the AIXM reasoning artifacts.
  @Override
  public String ontologyResource() {
    return "aixm-tbox.ttl";
  }

  @Override
  public String rulesResource() {
    return "aixm-ruleset-domain_range.rules";
  }
}
