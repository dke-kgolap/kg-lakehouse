package at.jku.dke.bigkgolap.index.support;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class Fixtures {

  private Fixtures() {}

  public static CubeSchema atmSchema() {
    return load("/fixtures/atm.yaml");
  }

  public static CubeSchema weatherSchema() {
    return load("/fixtures/weather.yaml");
  }

  private static CubeSchema load(String resource) {
    try (InputStream in = Fixtures.class.getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("Test fixture not found: " + resource);
      return CubeSchema.fromYaml(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
