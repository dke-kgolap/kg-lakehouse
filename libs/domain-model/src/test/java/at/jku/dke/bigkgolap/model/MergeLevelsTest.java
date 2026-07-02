package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MergeLevelsTest {

  private final CubeSchema schema = Fixtures.atmSchema();

  @Test
  void atLeavesPicksTheDeepestLevelPerDimension() {
    var ml = MergeLevels.atLeaves(schema);
    assertThat(ml.levelFor("time").name()).isEqualTo("day");
    assertThat(ml.levelFor("location").name()).isEqualTo("location");
    assertThat(ml.levelFor("topic").name()).isEqualTo("feature");
  }

  @Test
  void atRootsPicksTheShallowestLevelPerDimension() {
    var ml = MergeLevels.atRoots(schema);
    assertThat(ml.levelFor("time").name()).isEqualTo("year");
    assertThat(ml.levelFor("location").name()).isEqualTo("territory");
  }

  @Test
  void mismatchedDimensionKeyIsRejected() {
    var time = schema.locate("time", "year");
    assertThatThrownBy(() -> new MergeLevels(Map.of("location", time)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
