package at.jku.dke.bigkgolap.engines.fixm;

import at.jku.dke.bigkgolap.graph.RdfGraphBuilder;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FixmMapperTest {

  private final FixmMapper mapper = new FixmMapper();

  @Test
  void flightMappingEmitsTriplesIncludingFlightRootSubject() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.fixmFlight(), builder);
    var turtle = new String(builder.serialize("TURTLE"), StandardCharsets.UTF_8);
    Assertions.assertThat(turtle).contains("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    // rdf:type is an IRI (BASE_URI + localName), not a literal, so RDFS reasoning can fire.
    Assertions.assertThat(turtle).contains("fixm#Flight");
    Assertions.assertThat(turtle).doesNotContain("\"Flight\"");
    Assertions.assertThat(builder.tripleCount()).isGreaterThanOrEqualTo(2L);
  }

  @Test
  void rootFlightIsTypedEvenWithoutGmlId() throws Exception {
    // Real FIXM messages carry no gml:id; the root feature must still be typed fixm#Flight so the
    // TBox (Flight ⊑ Flights) can generalize it.
    String xml =
        "<fx:Flight xmlns:fx=\"http://www.fixm.aero/flight/4.3\""
            + " xmlns:fb=\"http://www.fixm.aero/base/4.3\">"
            + "<fx:flightIdentification><fx:aircraftIdentification>BAW6879"
            + "</fx:aircraftIdentification></fx:flightIdentification></fx:Flight>";
    var builder = new RdfGraphBuilder();
    mapper.map(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), builder);
    var nt = new String(builder.serialize("N-TRIPLES"), StandardCharsets.UTF_8);
    Assertions.assertThat(nt).contains("fixm#Flight");
    Assertions.assertThat(builder.tripleCount()).isGreaterThan(0);
  }

  @Test
  void routeTrajectoryElementsAreTypedAsTheDomainClass() throws Exception {
    String xml =
        "<fx:Flight xmlns:fx=\"http://www.fixm.aero/flight/4.3\">"
            + "<fx:routeTrajectoryGroup><fx:agreed>"
            + "<fx:element seqNum=\"0\"/><fx:element seqNum=\"1\"/>"
            + "</fx:agreed></fx:routeTrajectoryGroup></fx:Flight>";
    var builder = new RdfGraphBuilder();
    mapper.map(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), builder);
    var nt = new String(builder.serialize("N-TRIPLES"), StandardCharsets.UTF_8);
    // <fx:element> maps to fixm:RouteTrajectoryElement (taxonomy: ⊑ Trajectory ⊑ Flights).
    Assertions.assertThat(nt).contains("fixm#RouteTrajectoryElement");
    Assertions.assertThat(nt).contains("fixm#routeTrajectoryElement"); // edge from the Flight
  }

  @Test
  void serializeNTriplesRoundTripPreservesTripleCount() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.fixmFlight(), builder);
    var nt = new String(builder.serialize("N-TRIPLES"), StandardCharsets.UTF_8);
    long lineCount = nt.lines().filter(l -> l.trim().endsWith(".")).count();
    Assertions.assertThat(lineCount).isEqualTo(builder.tripleCount());
  }
}
