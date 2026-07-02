package at.jku.dke.bigkgolap.engines.iwxxm;

import at.jku.dke.bigkgolap.graph.RdfGraphBuilder;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class IwxxmMapperTest {

  private final IwxxmMapper mapper = new IwxxmMapper();

  @Test
  void metarMapperEmitsTriplesAndTagsSubjectAsMetarViaRdfTypeIri() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.metar(), builder);

    Assertions.assertThat(builder.tripleCount()).isGreaterThan(0);
    var nq = new String(builder.serialize("N-TRIPLES"), StandardCharsets.UTF_8);
    // rdf:type is an IRI (BASE_URI + localName), not a literal, so RDFS reasoning can fire.
    Assertions.assertThat(nq).contains("iwxxm#METAR");
    Assertions.assertThat(nq).doesNotContain("\"METAR\"");
    Assertions.assertThat(nq)
        .contains("http://example.org/bigkgolap/atm/iwxxm#metar-loww-2026050108");
  }

  @Test
  void tafMapperEmitsForecastTriples() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.taf(), builder);

    Assertions.assertThat(builder.tripleCount()).isGreaterThan(0);
    var nt = new String(builder.serialize("N-TRIPLES"), StandardCharsets.UTF_8);
    Assertions.assertThat(nt).contains("iwxxm#TAF");
    Assertions.assertThat(nt).doesNotContain("\"TAF\"");
  }

  @Test
  void sigmetMapperPreservesAnalysisDetails() throws Exception {
    var builder = new RdfGraphBuilder();
    mapper.map(Fixtures.sigmet(), builder);

    Assertions.assertThat(builder.tripleCount()).isGreaterThan(0);
    var nt = new String(builder.serialize("N-TRIPLES"), StandardCharsets.UTF_8);
    Assertions.assertThat(nt).contains("iwxxm#SIGMET");
    Assertions.assertThat(nt).doesNotContain("\"SIGMET\"");
    Assertions.assertThat(nt).contains("350");
  }
}
