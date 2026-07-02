package at.jku.dke.bigkgolap.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.query.exception.InvalidQueryException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryParserServiceTest {

  private CubeSchema schema;
  private QueryParserService parser;

  @BeforeEach
  void setUp() {
    schema = CubeSchema.fromYaml(getClass().getResourceAsStream("/fixtures/atm.yaml"));
    parser = new QueryParserService();
  }

  @Test
  void selectStarYieldsEmptySliceDiceAndNoRollup() {
    ParsedQuery parsed = parser.parseText("SELECT *", schema);
    assertThat(parsed.sliceDice().isEmpty()).isTrue();
    assertThat(parsed.mergeLevels()).isNull();
  }

  @Test
  void singlePredicateParsesDimensionAndValue() {
    ParsedQuery parsed = parser.parseText("SELECT location_fir=LOVV", schema);
    var locHierarchy = parsed.sliceDice().getHierarchy("location");
    assertThat(locHierarchy).isNotNull();
    assertThat(locHierarchy.leafMember().value()).isEqualTo("LOVV");
    assertThat(locHierarchy.leafMember().level().name()).isEqualTo("fir");
  }

  @Test
  void multiplePredicatesWithAnd() {
    ParsedQuery parsed =
        parser.parseText("SELECT location_fir=LOVV AND topic_category=AirportHeliport", schema);
    assertThat(parsed.sliceDice().hierarchies().keySet())
        .containsExactlyInAnyOrder("location", "topic");
  }

  @Test
  void rollupOnAddsMergeLevels() {
    ParsedQuery parsed =
        parser.parseText(
            "SELECT location_fir=LOVV ROLLUP ON time_year, location_territory", schema);
    assertThat(parsed.mergeLevels().levelFor("time").name()).isEqualTo("year");
    assertThat(parsed.mergeLevels().levelFor("location").name()).isEqualTo("territory");
  }

  @Test
  void dimAllRollupResolvesToDimensionRootLevel() {
    ParsedQuery parsed = parser.parseText("SELECT * ROLLUP ON time_ALL", schema);
    assertThat(parsed.mergeLevels().levelFor("time").name()).isEqualTo("year");
  }

  @Test
  void caseInsensitiveSelectAndRollupKeywords() {
    ParsedQuery parsed = parser.parseText("select location_fir=LOVV rollup on time_year", schema);
    assertThat(parsed.sliceDice().hierarchies().keySet()).contains("location");
    assertThat(parsed.mergeLevels().levelFor("time").name()).isEqualTo("year");
  }

  @Test
  void missingSelectKeywordIsRejected() {
    assertThatThrownBy(() -> parser.parseText("location_fir=LOVV", schema))
        .isInstanceOf(InvalidQueryException.class);
  }

  @Test
  void duplicateDimensionIsRejected() {
    assertThatThrownBy(
            () ->
                parser.parseText("SELECT location_fir=LOVV AND location_territory=Austria", schema))
        .isInstanceOf(InvalidQueryException.class)
        .hasMessageContaining("Duplicate dimension");
  }

  @Test
  void unknownDimensionPrefixIsRejected() {
    assertThatThrownBy(() -> parser.parseText("SELECT colour_red=ff0000", schema))
        .isInstanceOf(InvalidQueryException.class);
  }

  @Test
  void unknownLevelUnderKnownDimensionIsRejected() {
    assertThatThrownBy(() -> parser.parseText("SELECT location_galaxy=LOVV", schema))
        .isInstanceOf(InvalidQueryException.class);
  }

  @Test
  void predicateWithoutEqualsIsRejected() {
    assertThatThrownBy(() -> parser.parseText("SELECT location_fir-LOVV", schema))
        .isInstanceOf(InvalidQueryException.class);
  }

  @Test
  void structuredFormFullRoundTrip() {
    ParsedQuery parsed =
        parser.parseStructured(
            Map.of("location", Map.of("fir", "LOVV")),
            Map.of("time", "year"),
            GraphRepresentation.RDF,
            "application/n-quads",
            schema);
    assertThat(parsed.sliceDice().getHierarchy("location").leafMember().value()).isEqualTo("LOVV");
    assertThat(parsed.mergeLevels().levelFor("time").name()).isEqualTo("year");
  }

  @Test
  void structuredFormEmptySelectAndRollupIsEquivalentToSelectStar() {
    ParsedQuery parsed =
        parser.parseStructured(
            Map.of(), Map.of(), GraphRepresentation.RDF, "application/n-quads", schema);
    assertThat(parsed.sliceDice().isEmpty()).isTrue();
    assertThat(parsed.mergeLevels()).isNull();
  }

  @Test
  void structuredFormRejectsMultipleLevelEntriesPerDimension() {
    assertThatThrownBy(
            () ->
                parser.parseStructured(
                    Map.of("location", Map.of("fir", "LOVV", "territory", "Austria")),
                    Map.of(),
                    GraphRepresentation.RDF,
                    "application/n-quads",
                    schema))
        .isInstanceOf(InvalidQueryException.class);
  }
}
