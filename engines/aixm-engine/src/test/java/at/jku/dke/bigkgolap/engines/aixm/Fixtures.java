package at.jku.dke.bigkgolap.engines.aixm;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.InputStream;

class Fixtures {

  private Fixtures() {}

  static CubeSchema atmSchema() {
    try (var is = open("/fixtures/atm.yaml")) {
      return CubeSchema.fromYaml(is);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static CubeSchema weatherSchema() {
    try (var is = open("/fixtures/weather.yaml")) {
      return CubeSchema.fromYaml(is);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static InputStream aixmMultiFeature() {
    return open("/fixtures/aixm-multi-feature.xml");
  }

  static InputStream aixmNotam() {
    return open("/fixtures/aixm-notam.xml");
  }

  private static InputStream open(String resource) {
    var is = Fixtures.class.getResourceAsStream(resource);
    if (is == null) throw new IllegalStateException("Test fixture not found: " + resource);
    return is;
  }
}
