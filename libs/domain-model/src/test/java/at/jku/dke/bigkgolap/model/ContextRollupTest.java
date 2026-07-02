package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextRollupTest {

  private final CubeSchema schema = Fixtures.atmSchema();
  private final Level year = schema.locate("time", "year");
  private final Level month = schema.locate("time", "month");
  private final Level day = schema.locate("time", "day");
  private final Level territory = schema.locate("location", "territory");
  private final Level fir = schema.locate("location", "fir");
  private final Level location = schema.locate("location", "location");
  private final Level category = schema.locate("topic", "category");

  private final Hierarchy timeMonth =
      Hierarchy.of(new Member(year, "2018"), new Member(month, "2018-02"));
  private final Hierarchy timeDay =
      Hierarchy.of(
          new Member(year, "2018"), new Member(month, "2018-02"), new Member(day, "2018-02-15"));
  private final Hierarchy locLOWW =
      Hierarchy.of(
          new Member(territory, "Austria"), new Member(fir, "LOVV"), new Member(location, "LOWW"));
  private final Hierarchy locLOVV =
      Hierarchy.of(new Member(territory, "Austria"), new Member(fir, "LOVV"));
  private final Hierarchy topicCategory = Hierarchy.of(new Member(category, "AirportHeliport"));

  @Test
  void rollsUpToEqualContextRollsUpToItself() {
    var ctx = Context.of(List.of(timeDay, locLOWW, topicCategory), schema);
    assertThat(ctx.rollsUpTo(ctx, schema)).isTrue();
  }

  @Test
  void rollsUpToStrictPrefixPerDimension() {
    var specific = Context.of(List.of(timeDay, locLOWW, topicCategory), schema);
    var general = Context.of(List.of(timeMonth, locLOVV, topicCategory), schema);
    assertThat(specific.rollsUpTo(general, schema)).isTrue();
  }

  @Test
  void rollsUpToFalseWhenGeneralIsFinerThanSpecific() {
    var specific = Context.of(List.of(timeMonth, locLOVV, topicCategory), schema);
    var general = Context.of(List.of(timeDay, locLOWW, topicCategory), schema);
    assertThat(specific.rollsUpTo(general, schema)).isFalse();
  }

  @Test
  void rollsUpToFalseWhenAMemberAtTheSameDepthDiverges() {
    var specific = Context.of(List.of(timeDay, locLOWW, topicCategory), schema);
    var divergent =
        Context.of(List.of(Hierarchy.of(new Member(year, "2019")), locLOVV, topicCategory), schema);
    assertThat(specific.rollsUpTo(divergent, schema)).isFalse();
  }

  @Test
  void rollsUpToAllowsAllHierarchiesInTheGeneralContext() {
    var specific = Context.of(List.of(timeDay, locLOWW, topicCategory), schema);
    var onlyLocation = Context.of(List.of(locLOVV), schema);
    assertThat(specific.rollsUpTo(onlyLocation, schema)).isTrue();
  }

  @Test
  void rollUpToPerLldExampleMonthAndFirRollUpPartially() {
    var ctx = Context.of(List.of(timeMonth, locLOWW, topicCategory), schema);
    var merge = new MergeLevels(Map.of("time", day, "location", fir));
    var rolled = ctx.rollUpTo(merge, schema);
    assertThat(rolled.getHierarchy("time").members())
        .containsExactly(new Member(year, "2018"), new Member(month, "2018-02"));
    assertThat(rolled.getHierarchy("location").members())
        .containsExactly(new Member(territory, "Austria"), new Member(fir, "LOVV"));
    assertThat(rolled.getHierarchy("topic").members())
        .containsExactly(new Member(category, "AirportHeliport"));
  }

  @Test
  void rollUpToRootLevelKeepsOnlyTheRootMember() {
    var ctx = Context.of(List.of(timeDay, locLOWW, topicCategory), schema);
    var merge = new MergeLevels(Map.of("time", year, "location", territory));
    var rolled = ctx.rollUpTo(merge, schema);
    assertThat(rolled.getHierarchy("time").members()).containsExactly(new Member(year, "2018"));
    assertThat(rolled.getHierarchy("location").members())
        .containsExactly(new Member(territory, "Austria"));
    assertThat(rolled.getHierarchy("topic").members())
        .containsExactly(new Member(category, "AirportHeliport"));
  }

  @Test
  void rollUpToWithEmptyMergeLevelsIsIdentity() {
    var ctx = Context.of(List.of(timeDay, locLOWW, topicCategory), schema);
    var rolled = ctx.rollUpTo(new MergeLevels(Map.of()), schema);
    assertThat(rolled.id()).isEqualTo(ctx.id());
  }

  @Test
  void rollUpToOnAllHierarchyStaysAll() {
    var ctx = Context.of(List.of(timeMonth), schema);
    var merge = new MergeLevels(Map.of("time", year, "location", territory, "topic", category));
    var rolled = ctx.rollUpTo(merge, schema);
    assertThat(rolled.getHierarchy("location").isAllLevel()).isTrue();
    assertThat(rolled.getHierarchy("topic").isAllLevel()).isTrue();
    assertThat(rolled.getHierarchy("time").members()).containsExactly(new Member(year, "2018"));
  }

  @Test
  void rollUpToCannotGoFinerThanStoredMonthStaysAtMonthWhenMergeSaysDay() {
    var ctx = Context.of(List.of(timeMonth, locLOVV, topicCategory), schema);
    var rolled = ctx.rollUpTo(new MergeLevels(Map.of("time", day)), schema);
    assertThat(rolled.getHierarchy("time").members()).isEqualTo(timeMonth.members());
  }
}
