package at.jku.dke.bigkgolap.engines.fixm;

import at.jku.dke.bigkgolap.engine.Engines;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FixmEngineDiscoveryTest {

  @Test
  void byIdReturnsFixmEngineForFixm() {
    Assertions.assertThat(Engines.byId("fixm")).isInstanceOf(FixmEngine.class);
  }

  @Test
  void byIdIsCaseInsensitive() {
    Assertions.assertThat(Engines.byId("FIXM")).isInstanceOf(FixmEngine.class);
    Assertions.assertThat(Engines.byId("Fixm")).isInstanceOf(FixmEngine.class);
  }

  @Test
  void findResolvesByMediaType() {
    Assertions.assertThat(Engines.find("application/xml+fixm")).isInstanceOf(FixmEngine.class);
    Assertions.assertThat(Engines.find("APPLICATION/XML+FIXM")).isInstanceOf(FixmEngine.class);
  }

  @Test
  void unknownMediaTypeReturnsNullForFixm() {
    Assertions.assertThat(Engines.find("application/xml+aixm")).isNull();
  }
}
