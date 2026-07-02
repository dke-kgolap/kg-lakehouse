package at.jku.dke.bigkgolap.engines.fixm;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FixmAnalyzerTest {

  private final FixmAnalyzer analyzer = new FixmAnalyzer();

  @Test
  void flightFixtureResolvesLocationToLOWW() throws Exception {
    var result = analyzer.analyze(Fixtures.fixmFlight(), Fixtures.fixmSchema());
    var locations =
        result.hierarchies().stream().filter(h -> "location".equals(h.dimension())).toList();
    Assertions.assertThat(locations).hasSize(1);
    Assertions.assertThat(locations.get(0).leafMember().value()).isEqualTo("LOWW");
  }

  @Test
  void flightFixtureResolvesTopicToFlight() throws Exception {
    var result = analyzer.analyze(Fixtures.fixmFlight(), Fixtures.fixmSchema());
    var topics = result.hierarchies().stream().filter(h -> "topic".equals(h.dimension())).toList();
    Assertions.assertThat(topics).hasSize(1);
    Assertions.assertThat(topics.get(0).leafMember().value()).isEqualTo("Flight");
  }

  @Test
  void flightFixtureResolvesTimeToDepartureDayOf20250101() throws Exception {
    var result = analyzer.analyze(Fixtures.fixmFlight(), Fixtures.fixmSchema());
    var times = result.hierarchies().stream().filter(h -> "time".equals(h.dimension())).toList();
    Assertions.assertThat(times).hasSize(1);
    Assertions.assertThat(times.get(0).leafMember().value()).isEqualTo("2025-01-01");
  }

  @Test
  void analyzerReturnsEmptyForDocumentWithNoFxDeparture() {
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <fx:Flight xmlns:fx="http://www.fixm.aero/flight/4.3"
                   xmlns:gml="http://www.opengis.net/gml/3.2"
                   gml:id="atm-gen.flight.empty"/>
        """;
    var result =
        analyzer.analyze(
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Fixtures.fixmSchema());
    Assertions.assertThat(result.featureContexts()).isEmpty();
  }
}
