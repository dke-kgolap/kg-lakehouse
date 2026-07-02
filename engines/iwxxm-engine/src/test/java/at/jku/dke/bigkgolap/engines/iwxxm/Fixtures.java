package at.jku.dke.bigkgolap.engines.iwxxm;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.InputStream;

class Fixtures {

  private Fixtures() {}

  static CubeSchema meteoSchema() {
    try (var is = open("/fixtures/meteo.yaml")) {
      return CubeSchema.fromYaml(is);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static InputStream metar() {
    return open("/fixtures/iwxxm-metar.xml");
  }

  static InputStream taf() {
    return open("/fixtures/iwxxm-taf.xml");
  }

  static InputStream sigmet() {
    return open("/fixtures/iwxxm-sigmet.xml");
  }

  static InputStream airmet() {
    return open("/fixtures/iwxxm-airmet.xml");
  }

  static InputStream malformed() {
    return open("/fixtures/iwxxm-malformed.xml");
  }

  private static InputStream open(String resource) {
    var is = Fixtures.class.getResourceAsStream(resource);
    if (is == null) throw new IllegalStateException("Test fixture not found: " + resource);
    return is;
  }
}
