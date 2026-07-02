package at.jku.dke.bigkgolap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SliceDiceContextTest {

  private final CubeSchema schema = Fixtures.atmSchema();
  private final Level year = schema.locate("time", "year");
  private final Hierarchy timeYear2025 = Hierarchy.of(List.of(new Member(year, "2025")));

  @Test
  void doesNotFillMissingDimensions() {
    var sd = SliceDiceContext.of(List.of(timeYear2025), schema);
    assertThat(sd.hierarchies().keySet()).containsExactly("time");
    assertThat(sd.getHierarchy("location")).isNull();
  }

  @Test
  void unknownDimensionIsRejected() {
    assertThatThrownBy(() -> SliceDiceContext.of(List.of(Hierarchy.all("ghost")), schema))
        .isInstanceOf(InvalidContextException.class);
  }

  @Test
  void emptySliceDiceIsAllowed() {
    var sd = SliceDiceContext.empty();
    assertThat(sd.isEmpty()).isTrue();
  }
}
