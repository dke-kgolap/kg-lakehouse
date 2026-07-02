package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ContextTest {

  private final CubeSchema schema = Fixtures.atmSchema();
  private final Level year = schema.locate("time", "year");
  private final Level month = schema.locate("time", "month");
  private final Level territory = schema.locate("location", "territory");
  private final Level fir = schema.locate("location", "fir");

  private final Hierarchy timeYear2025 = Hierarchy.of(List.of(new Member(year, "2025")));
  private final Hierarchy timeJan2025 =
      Hierarchy.of(List.of(new Member(year, "2025"), new Member(month, "2025-01")));
  private final Hierarchy locFirLOVV =
      Hierarchy.of(List.of(new Member(territory, "Austria"), new Member(fir, "LOVV")));

  @Test
  void missingDimensionsAreFilledWithAllLevelHierarchies() {
    var ctx = Context.of(List.of(timeYear2025), schema);
    assertThat(ctx.hierarchies().keySet()).containsExactly("location", "time", "topic");
    assertThat(ctx.getHierarchy("location").isAllLevel()).isTrue();
    assertThat(ctx.getHierarchy("topic").isAllLevel()).isTrue();
  }

  @Test
  void idIsIndependentOfInputOrder() {
    var a = Context.of(List.of(timeYear2025, locFirLOVV), schema);
    var b = Context.of(List.of(locFirLOVV, timeYear2025), schema);
    assertThat(a.id()).isEqualTo(b.id());
  }

  @Test
  void partialContextEqualsFullyPaddedContextWithAlls() {
    var partial = Context.of(List.of(timeYear2025), schema);
    var padded =
        Context.of(
            List.of(timeYear2025, Hierarchy.all("location"), Hierarchy.all("topic")), schema);
    assertThat(partial.id()).isEqualTo(padded.id());
  }

  @Test
  void idIsSha256HexShaped() {
    var ctx = Context.of(List.of(timeYear2025), schema);
    assertThat(ctx.id()).matches("[0-9a-f]{64}");
  }

  @Test
  void idChangesWhenAnyMemberValueChanges() {
    var a = Context.of(List.of(timeYear2025), schema);
    var b = Context.of(List.of(Hierarchy.of(List.of(new Member(year, "2024")))), schema);
    assertThat(a.id()).isNotEqualTo(b.id());
  }

  @Test
  void flatMembersExposesDimLevelnameValuePairsAndSkipsAll() {
    var ctx = Context.of(List.of(timeJan2025, locFirLOVV), schema);
    var flat = ctx.flatMembers();
    assertThat(flat).containsEntry("time_year", "2025");
    assertThat(flat).containsEntry("time_month", "2025-01");
    assertThat(flat).containsEntry("location_territory", "Austria");
    assertThat(flat).containsEntry("location_fir", "LOVV");
    assertThat(flat).doesNotContainKey("topic_category");
  }

  @Test
  void unknownDimensionIsRejected() {
    var bogus = Hierarchy.all("ghost");
    assertThatThrownBy(() -> Context.of(List.of(bogus), schema))
        .isInstanceOf(InvalidContextException.class);
  }

  @Test
  void idIsDeterministicAcrossManyRandomConstructionOrders() {
    var hierarchies = List.of(timeJan2025, locFirLOVV);
    String expectedId = Context.of(hierarchies, schema).id();
    var rng = new Random(42);
    for (int i = 0; i < 1_000; i++) {
      var shuffled = new ArrayList<>(hierarchies);
      java.util.Collections.shuffle(shuffled, rng);
      assertThat(Context.of(shuffled, schema).id()).isEqualTo(expectedId);
    }
  }
}
