package at.jku.dke.bigkgolap.engines.aixm;

import at.jku.dke.bigkgolap.graph.RdfGraphBuilder;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AixmMapperTest {

  private final AixmMapper mapper = new AixmMapper();

  @Test
  void multiFeatureMappingEmitsTriplesForBothFeatures() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.aixmMultiFeature(), builder);

    var turtle = new String(builder.serialize("TURTLE"), StandardCharsets.UTF_8);
    Assertions.assertThat(turtle).contains("STAND-1");
    Assertions.assertThat(turtle).contains("APRON-1");
    Assertions.assertThat(builder.tripleCount()).isGreaterThanOrEqualTo(4L);
  }

  @Test
  void mapperProducesJenaModelFromBuild() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.aixmMultiFeature(), builder);
    var model = builder.build();
    Assertions.assertThat(model).isNotNull();
  }

  @Test
  void serializeNTriplesRoundTripPreservesTripleCount() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.aixmMultiFeature(), builder);
    var nt = new String(builder.serialize("N-TRIPLES"), StandardCharsets.UTF_8);
    Assertions.assertThat(nt).isNotEmpty();
    long lineCount = nt.lines().filter(l -> l.trim().endsWith(".")).count();
    Assertions.assertThat(lineCount).isEqualTo(builder.tripleCount());
  }

  @Test
  void notamMappingEmitsEventEventTypeTriplesAndNotamPropertyTriples() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.aixmNotam(), builder);

    var turtle = new String(builder.serialize("TURTLE"), StandardCharsets.UTF_8);
    Assertions.assertThat(turtle).contains("NOTAM-001");
    Assertions.assertThat(turtle).contains("aixm#Event");
    Assertions.assertThat(builder.tripleCount()).isGreaterThanOrEqualTo(3L);
  }

  @Test
  void rdfTypeIsEmittedAsIriSoRdfsReasoningCanFire() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.aixmMultiFeature(), builder);
    var turtle = new String(builder.serialize("TURTLE"), StandardCharsets.UTF_8);
    // Types are IRIs (BASE_URI + localName), not string literals, so they align with the
    // generated subClassOf TBox and RDFS reasoning can derive super-types.
    Assertions.assertThat(turtle).contains("aixm#AircraftStand");
    Assertions.assertThat(turtle).contains("aixm#ApronElement");
    Assertions.assertThat(turtle).doesNotContain("\"AircraftStand\"");
    Assertions.assertThat(turtle).doesNotContain("\"ApronElement\"");
  }
}
