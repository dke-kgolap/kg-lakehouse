package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.model.rollup.RollUpFun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RollUpFunTest {

  @AfterEach
  void resetRegistry() {
    RollUpFun.resetToBuiltIns();
  }

  @Test
  void dateToMonthConvertsAYyyyMmDdValueToYyyyMm() {
    var schema = Fixtures.atmSchema();
    var day = schema.locate("time", "day");
    var rolled = RollUpFun.rollUp(new Member(day, "2025-01-15"), schema);
    assertThat(rolled).isNotNull();
    assertThat(rolled.level().name()).isEqualTo("month");
    assertThat(rolled.value()).isEqualTo("2025-01");
  }

  @Test
  void dateToMonthRejectsANonDayShapedValue() {
    var schema = Fixtures.atmSchema();
    var day = schema.locate("time", "day");
    assertThatThrownBy(() -> RollUpFun.rollUp(new Member(day, "2025-01"), schema))
        .isInstanceOf(HierarchyNotAvailableException.class);
  }

  @Test
  void dateToYearConvertsYyyyMmToYyyy() {
    var schema = Fixtures.atmSchema();
    var month = schema.locate("time", "month");
    var rolled = RollUpFun.rollUp(new Member(month, "2025-01"), schema);
    assertThat(rolled).isNotNull();
    assertThat(rolled.level().name()).isEqualTo("year");
    assertThat(rolled.value()).isEqualTo("2025");
  }

  @Test
  void dateToYearRejectsYyyyAlone() {
    var schema = Fixtures.atmSchema();
    var month = schema.locate("time", "month");
    assertThatThrownBy(() -> RollUpFun.rollUp(new Member(month, "2025"), schema))
        .isInstanceOf(HierarchyNotAvailableException.class);
  }

  @Test
  void lookupWalksTheLocationHierarchy() {
    var schema = Fixtures.atmSchema();
    var location = schema.locate("location", "location");
    var fir = schema.locate("location", "fir");
    var toFir = RollUpFun.rollUp(new Member(location, "LOWW"), schema);
    assertThat(toFir.level().name()).isEqualTo("fir");
    assertThat(toFir.value()).isEqualTo("LOVV");
    var toTerritory = RollUpFun.rollUp(toFir, schema);
    assertThat(toTerritory.level().name()).isEqualTo("territory");
    assertThat(toTerritory.value()).isEqualTo("Austria");
    assertThat(RollUpFun.rollUp(toTerritory, schema)).isNull();
    assertThat(fir.depth()).isEqualTo(1);
  }

  @Test
  void lookupWithNoMatchingRowThrows() {
    var schema = Fixtures.atmSchema();
    var location = schema.locate("location", "location");
    assertThatThrownBy(() -> RollUpFun.rollUp(new Member(location, "XXXX"), schema))
        .isInstanceOf(HierarchyNotAvailableException.class);
  }

  @Test
  void customRollupFunctionCanBeRegisteredAndResolvedByTheLoader() {
    RollUpFun.register(
        "test:upper",
        (m, schema) -> {
          var parent = schema.locate(m.level().dimension(), m.level().rollupTo());
          return new Member(parent, m.value().toUpperCase());
        });
    String yaml =
        """
                schema:
                  id: customfn
                  dimensions:
                    tag:
                      levels:
                        - { name: family, depth: 0, rollup_to: null, rollup_function: null }
                        - { name: leaf,   depth: 1, rollup_to: family, rollup_function: "test:upper" }
                """;
    var schema = CubeSchema.fromYaml(new java.io.ByteArrayInputStream(yaml.getBytes()));
    var leaf = schema.locate("tag", "leaf");
    var rolled = RollUpFun.rollUp(new Member(leaf, "abc"), schema);
    assertThat(rolled.value()).isEqualTo("ABC");
  }
}
