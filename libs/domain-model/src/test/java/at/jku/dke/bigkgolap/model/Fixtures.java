package at.jku.dke.bigkgolap.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class Fixtures {

  private Fixtures() {}

  static InputStream atm() {
    return open("/fixtures/atm.yaml");
  }

  static InputStream weather() {
    return open("/fixtures/weather.yaml");
  }

  static byte[] atmYamlBytes() {
    try (var in = open("/fixtures/atm.yaml")) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static byte[] weatherYamlBytes() {
    try (var in = open("/fixtures/weather.yaml")) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static CubeSchema atmSchema() {
    try (var in = atm()) {
      return CubeSchema.fromYaml(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static CubeSchema weatherSchema() {
    try (var in = weather()) {
      return CubeSchema.fromYaml(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static InputStream open(String resource) {
    InputStream in = Fixtures.class.getResourceAsStream(resource);
    if (in == null) throw new IllegalStateException("Test fixture not found: " + resource);
    return in;
  }
}
