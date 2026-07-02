package at.jku.dke.bigkgolap.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.Level;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.MergeLevels;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MergeAndPropagateServiceTest {

  private CubeSchema schema;
  private MergeAndPropagateService service;

  @BeforeEach
  void setUp() {
    schema = CubeSchema.fromYaml(getClass().getResourceAsStream("/fixtures/atm.yaml"));
    service = new MergeAndPropagateService();
  }

  private Level year() {
    return schema.locate("time", "year");
  }

  private Level month() {
    return schema.locate("time", "month");
  }

  private Level day() {
    return schema.locate("time", "day");
  }

  private Level territory() {
    return schema.locate("location", "territory");
  }

  private Level fir() {
    return schema.locate("location", "fir");
  }

  private Level location() {
    return schema.locate("location", "location");
  }

  private Level category() {
    return schema.locate("topic", "category");
  }

  private Context ctx(Hierarchy... hierarchies) {
    return Context.of(List.of(hierarchies), schema);
  }

  private Hierarchy timeYear() {
    return timeYear("2018");
  }

  private Hierarchy timeYear(String value) {
    return Hierarchy.of(new Member(year(), value));
  }

  private Hierarchy timeMonth() {
    return timeMonth("2018-02");
  }

  private Hierarchy timeMonth(String value) {
    return Hierarchy.of(new Member(year(), value.substring(0, 4)), new Member(month(), value));
  }

  private Hierarchy timeDay() {
    return timeDay("2018-02-15");
  }

  private Hierarchy timeDay(String value) {
    return Hierarchy.of(
        new Member(year(), value.substring(0, 4)),
        new Member(month(), value.substring(0, 7)),
        new Member(day(), value));
  }

  private Hierarchy locTerritory() {
    return locTerritory("Austria");
  }

  private Hierarchy locTerritory(String value) {
    return Hierarchy.of(new Member(territory(), value));
  }

  private Hierarchy locFir() {
    return Hierarchy.of(new Member(territory(), "Austria"), new Member(fir(), "LOVV"));
  }

  private Hierarchy locLOWW() {
    return Hierarchy.of(
        new Member(territory(), "Austria"),
        new Member(fir(), "LOVV"),
        new Member(location(), "LOWW"));
  }

  private Hierarchy topicCategory() {
    return Hierarchy.of(new Member(category(), "AirportHeliport"));
  }

  @Test
  void identityWhenMergeLevelsIsNull() {
    Context a = ctx(timeMonth(), locFir(), topicCategory());
    Context b = ctx(timeYear(), locTerritory(), topicCategory());
    var result = service.mergeAndPropagate(schema, Set.of(a, b), null);
    assertThat(result.finalContexts()).containsExactlyInAnyOrder(a, b);
    assertThat(result.contextMap().get(a.id())).containsExactly(a);
    assertThat(result.contextMap().get(b.id())).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void partialRollupKeepsMonthAtMonthWhenMergeSaysDay() {
    Context stored = ctx(timeMonth(), locLOWW(), topicCategory());
    MergeLevels merge = new MergeLevels(Map.of("time", day(), "location", fir()));
    var result = service.mergeAndPropagate(schema, Set.of(stored), merge);
    assertThat(result.finalContexts()).hasSize(1);
    Context rolled = result.finalContexts().iterator().next();
    assertThat(rolled.getHierarchy("time").members())
        .containsExactly(new Member(year(), "2018"), new Member(month(), "2018-02"));
    assertThat(rolled.getHierarchy("location").members())
        .containsExactly(new Member(territory(), "Austria"), new Member(fir(), "LOVV"));
  }

  @Test
  void rollUpToRootLevelProducesASingleCoarseFinalContext() {
    Context a = ctx(timeDay(), locLOWW(), topicCategory());
    Context b = ctx(timeDay("2018-02-16"), locLOWW(), topicCategory());
    MergeLevels merge =
        new MergeLevels(Map.of("time", year(), "location", territory(), "topic", category()));
    var result = service.mergeAndPropagate(schema, Set.of(a, b), merge);
    assertThat(result.finalContexts()).hasSize(1);
  }

  @Test
  void nonRollingUpContextsAreIndependent() {
    Context a = ctx(timeYear("2018"), locTerritory("Austria"), topicCategory());
    Context b = ctx(timeYear("2019"), locTerritory("Germany"), topicCategory());
    var result = service.mergeAndPropagate(schema, Set.of(a, b), null);
    assertThat(result.contextMap().get(a.id())).containsExactly(a);
    assertThat(result.contextMap().get(b.id())).containsExactly(b);
  }
}
