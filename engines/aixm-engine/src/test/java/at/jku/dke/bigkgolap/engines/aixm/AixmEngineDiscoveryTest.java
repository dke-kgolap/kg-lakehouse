package at.jku.dke.bigkgolap.engines.aixm;

import at.jku.dke.bigkgolap.engine.Engines;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AixmEngineDiscoveryTest {

  @Test
  void byIdReturnsAixmEngineForAixm() {
    Assertions.assertThat(Engines.byId("aixm")).isInstanceOf(AixmEngine.class);
  }

  @Test
  void byIdIsCaseInsensitive() {
    Assertions.assertThat(Engines.byId("AIXM")).isInstanceOf(AixmEngine.class);
    Assertions.assertThat(Engines.byId("Aixm")).isInstanceOf(AixmEngine.class);
  }

  @Test
  void findResolvesByMediaType() {
    Assertions.assertThat(Engines.find("application/xml+aixm")).isInstanceOf(AixmEngine.class);
    Assertions.assertThat(Engines.find("APPLICATION/XML+AIXM")).isInstanceOf(AixmEngine.class);
  }

  @Test
  void unknownMediaTypeReturnsNull() {
    Assertions.assertThat(Engines.find("application/xml+iwxxm")).isNull();
    Assertions.assertThat(Engines.byId("unknown")).isNull();
  }
}
