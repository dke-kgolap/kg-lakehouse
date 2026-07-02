package at.jku.dke.bigkgolap.engines.iwxxm;

import at.jku.dke.bigkgolap.engine.Engines;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class IwxxmEngineDiscoveryTest {

  @Test
  void byIdReturnsIwxxmEngineForIwxxm() {
    Assertions.assertThat(Engines.byId("iwxxm")).isInstanceOf(IwxxmEngine.class);
  }

  @Test
  void byIdIsCaseInsensitive() {
    Assertions.assertThat(Engines.byId("IWXXM")).isInstanceOf(IwxxmEngine.class);
    Assertions.assertThat(Engines.byId("Iwxxm")).isInstanceOf(IwxxmEngine.class);
  }

  @Test
  void findResolvesByMediaType() {
    Assertions.assertThat(Engines.find("application/xml+iwxxm")).isInstanceOf(IwxxmEngine.class);
    Assertions.assertThat(Engines.find("APPLICATION/XML+IWXXM")).isInstanceOf(IwxxmEngine.class);
  }
}
