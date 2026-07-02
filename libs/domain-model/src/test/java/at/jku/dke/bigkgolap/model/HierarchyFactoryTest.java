package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HierarchyFactoryTest {

  private final CubeSchema schema = Fixtures.atmSchema();

  @Test
  void createFromALeafLocationMemberWalksAllTheWayToTheRoot() {
    var location = schema.locate("location", "location");
    var h = HierarchyFactory.create(new Member(location, "LOWW"), schema);
    assertThat(h.members()).hasSize(3);
    assertThat(h.members().stream().map(m -> m.level().name()).toList())
        .containsExactly("territory", "fir", "location");
    assertThat(h.members().stream().map(Member::value).toList())
        .containsExactly("Austria", "LOVV", "LOWW");
  }

  @Test
  void createFromARootMemberYieldsASingleMemberHierarchy() {
    var year = schema.locate("time", "year");
    var h = HierarchyFactory.create(new Member(year, "2025"), schema);
    assertThat(h.members()).hasSize(1);
    assertThat(h.leafMember().value()).isEqualTo("2025");
  }

  @Test
  void createWithTheLevelAndValueOverload() {
    var location = schema.locate("location", "location");
    var h = HierarchyFactory.create(location, "LOWS", schema);
    assertThat(h.members().get(h.members().size() - 1).value()).isEqualTo("LOWS");
  }
}
