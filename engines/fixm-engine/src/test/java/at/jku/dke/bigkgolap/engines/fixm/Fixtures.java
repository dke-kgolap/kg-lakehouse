package at.jku.dke.bigkgolap.engines.fixm;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.InputStream;

class Fixtures {

  private Fixtures() {}

  static CubeSchema fixmSchema() {
    try (var is = open("/fixtures/fixm.yaml")) {
      return CubeSchema.fromYaml(is);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static InputStream fixmFlight() {
    return open("/fixtures/fixm-flight.xml");
  }

  private static InputStream open(String resource) {
    var is = Fixtures.class.getResourceAsStream(resource);
    if (is == null) throw new IllegalStateException("Test fixture not found: " + resource);
    return is;
  }
}
