package at.jku.dke.bigkgolap.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoCacheTest {

  @Test
  void loadGraphAlwaysReturnsNull() {
    assertThat(NoCache.INSTANCE.loadGraph("atm", "ctx-1", GraphRepresentation.RDF)).isNull();
  }

  @Test
  void upsertDeleteClearAreNoOpsAndNeverThrow() {
    assertThatCode(
            () -> {
              NoCache.INSTANCE.upsertGraph(
                  "atm", "ctx-1", GraphRepresentation.RDF, new byte[] {1, 2, 3});
              NoCache.INSTANCE.deleteCachedGraphs(
                  "atm", List.of("ctx-1", "ctx-2"), List.of(GraphRepresentation.RDF));
              NoCache.INSTANCE.clear();
            })
        .doesNotThrowAnyException();
    assertThat(NoCache.INSTANCE.loadGraph("atm", "ctx-1", GraphRepresentation.RDF)).isNull();
  }
}
