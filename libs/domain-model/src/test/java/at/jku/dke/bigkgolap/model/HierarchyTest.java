package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HierarchyTest {

  private final CubeSchema schema = Fixtures.atmSchema();
  private final Level territory = schema.locate("location", "territory");
  private final Level fir = schema.locate("location", "fir");
  private final Level location = schema.locate("location", "location");

  @Test
  void idIsStableForSemanticallyEqualHierarchies() {
    var h1 =
        Hierarchy.of(
            List.of(
                new Member(territory, "Austria"),
                new Member(fir, "LOVV"),
                new Member(location, "LOWW")));
    var h2 =
        Hierarchy.of(
            List.of(
                new Member(territory, "Austria"),
                new Member(fir, "LOVV"),
                new Member(location, "LOWW")));
    assertThat(h1.id()).isEqualTo(h2.id());
    assertThat(h1).isEqualTo(h2);
  }

  @Test
  void idChangesWhenAnyValueChanges() {
    var base =
        Hierarchy.of(
            List.of(
                new Member(territory, "Austria"),
                new Member(fir, "LOVV"),
                new Member(location, "LOWW")));
    var differ =
        Hierarchy.of(
            List.of(
                new Member(territory, "Austria"),
                new Member(fir, "LOVV"),
                new Member(location, "LOWS")));
    assertThat(base.id()).isNotEqualTo(differ.id());
  }

  @Test
  void idIsSha256HexShaped() {
    var h = Hierarchy.all("time");
    assertThat(h.id()).matches("[0-9a-f]{64}");
  }

  @Test
  void allLevelHierarchyIsEmptyAndHasStableIdPerDimension() {
    var a1 = Hierarchy.all("time");
    var a2 = Hierarchy.all("time");
    assertThat(a1.isAllLevel()).isTrue();
    assertThat(a1.members()).isEmpty();
    assertThat(a1.id()).isEqualTo(a2.id());
    assertThat(Hierarchy.all("location").id()).isNotEqualTo(a1.id());
  }

  @Test
  void rollUpDropsTheLeafMember() {
    var h =
        Hierarchy.of(
            List.of(
                new Member(territory, "Austria"),
                new Member(fir, "LOVV"),
                new Member(location, "LOWW")));
    var parent = h.rollUp(schema);
    assertThat(parent.members()).hasSize(2);
    assertThat(parent.leafMember().value()).isEqualTo("LOVV");
  }

  @Test
  void rollUpOnSingleMemberHierarchyYieldsAllLevel() {
    var h = Hierarchy.of(List.of(new Member(territory, "Austria")));
    var parent = h.rollUp(schema);
    assertThat(parent.isAllLevel()).isTrue();
  }

  @Test
  void rollUpOnAllLevelThrows() {
    assertThatThrownBy(() -> Hierarchy.all("time").rollUp(schema))
        .isInstanceOf(CannotRollUpAllLevelException.class);
  }

  @Test
  void hierarchyOfWithMixedDimensionsIsRejected() {
    var time = schema.locate("time", "year");
    assertThatThrownBy(
            () -> Hierarchy.of(List.of(new Member(territory, "Austria"), new Member(time, "2025"))))
        .isInstanceOf(InvalidHierarchyException.class);
  }

  @Test
  void hierarchyOfWithNonContiguousDepthsIsRejected() {
    assertThatThrownBy(
            () ->
                Hierarchy.of(
                    List.of(new Member(territory, "Austria"), new Member(location, "LOWW"))))
        .isInstanceOf(InvalidHierarchyException.class);
  }

  @Test
  void hierarchyOfWithEmptyListIsRejected() {
    assertThatThrownBy(() -> Hierarchy.of(List.of())).isInstanceOf(InvalidHierarchyException.class);
  }
}
