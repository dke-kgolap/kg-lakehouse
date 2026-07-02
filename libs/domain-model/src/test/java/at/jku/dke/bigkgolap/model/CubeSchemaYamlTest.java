package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CubeSchemaYamlTest {

  @Test
  void loadsAtmFixtureAndExposesDimensionsInSortedOrder() {
    var schema = Fixtures.atmSchema();
    assertThat(schema.id()).isEqualTo("atm");
    assertThat(schema.dimensionNames()).containsExactly("location", "time", "topic");
    assertThat(schema.levelsOf("time").stream().map(Level::name).toList())
        .containsExactly("year", "month", "day");
    assertThat(schema.levelsOf("location").stream().map(Level::name).toList())
        .containsExactly("territory", "fir", "location");
  }

  @Test
  void loadingTheSameYamlTwiceProducesEqualSchemas() {
    var a = Fixtures.atmSchema();
    var b = Fixtures.atmSchema();
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void rollsUpToWalksTheChainToAParentLevel() {
    var schema = Fixtures.atmSchema();
    var day = schema.locate("time", "day");
    var year = schema.locate("time", "year");
    var month = schema.locate("time", "month");
    assertThat(schema.rollsUpTo(day, year)).isTrue();
    assertThat(schema.rollsUpTo(day, month)).isTrue();
    assertThat(schema.rollsUpTo(year, day)).isFalse();
    assertThat(schema.rollsUpTo(day, schema.locate("location", "territory"))).isFalse();
  }

  @Test
  void missingIdIsRejected() {
    String yaml =
        """
                schema:
                  dimensions:
                    time:
                      levels:
                        - { name: year, depth: 0, rollup_to: null, rollup_function: null }
                """;
    assertThatThrownBy(() -> CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes())))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("id");
  }

  @Test
  void idWithInvalidCharsIsRejected() {
    String yaml =
        """
                schema:
                  id: "BadId!"
                  dimensions:
                    time:
                      levels:
                        - { name: year, depth: 0, rollup_to: null, rollup_function: null }
                """;
    assertThatThrownBy(() -> CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes())))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("BadId!");
  }

  @Test
  void nonContiguousDepthsAreRejected() {
    String yaml =
        """
                schema:
                  id: bad
                  dimensions:
                    time:
                      levels:
                        - { name: year, depth: 0, rollup_to: null, rollup_function: null }
                        - { name: day,  depth: 2, rollup_to: year, rollup_function: "builtin:date_to_year" }
                """;
    assertThatThrownBy(() -> CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes())))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("contiguous");
  }

  @Test
  void unknownRollupToIsRejected() {
    String yaml =
        """
                schema:
                  id: bad
                  dimensions:
                    time:
                      levels:
                        - { name: year,  depth: 0, rollup_to: null, rollup_function: null }
                        - { name: month, depth: 1, rollup_to: decade, rollup_function: "builtin:date_to_year" }
                """;
    assertThatThrownBy(() -> CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes())))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("decade");
  }

  @Test
  void unknownRollupFunctionIsRejected() {
    String yaml =
        """
                schema:
                  id: bad
                  dimensions:
                    time:
                      levels:
                        - { name: year,  depth: 0, rollup_to: null, rollup_function: null }
                        - { name: month, depth: 1, rollup_to: year, rollup_function: "builtin:does_not_exist" }
                """;
    assertThatThrownBy(() -> CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes())))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("does_not_exist");
  }

  @Test
  void lookupLevelMissingFromHierarchyRowIsRejected() {
    String yaml =
        """
                schema:
                  id: bad
                  dimensions:
                    location:
                      levels:
                        - { name: territory, depth: 0, rollup_to: null, rollup_function: null }
                        - { name: fir, depth: 1, rollup_to: territory, rollup_function: "lookup" }
                  hierarchies:
                    location:
                      - { territory: Austria }
                """;
    assertThatThrownBy(() -> CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes())))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("fir");
  }

  @Test
  void hierarchiesForUnknownDimensionIsRejected() {
    String yaml =
        """
                schema:
                  id: bad
                  dimensions:
                    time:
                      levels:
                        - { name: year, depth: 0, rollup_to: null, rollup_function: null }
                  hierarchies:
                    ghost:
                      - { foo: bar }
                """;
    assertThatThrownBy(() -> CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes())))
        .isInstanceOf(InvalidCubeSchemaException.class)
        .hasMessageContaining("ghost");
  }
}
