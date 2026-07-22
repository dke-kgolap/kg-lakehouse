package at.jku.dke.bigkgolap.index.testing;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Resolution semantics of the in-memory index against the KG-OLAP model (Definitions 3 (vi) and 4)
 * and against the Cassandra implementation it stands in for.
 */
class InMemoryIndexRepositoryResolutionTest {

  private final CubeSchema atm = loadAtmSchema();
  private final InMemoryIndexRepository repo = new InMemoryIndexRepository();

  private static CubeSchema loadAtmSchema() {
    try (InputStream in =
        InMemoryIndexRepositoryResolutionTest.class.getResourceAsStream("/fixtures/atm.yaml")) {
      if (in == null) throw new IllegalStateException("Test fixture not found: /fixtures/atm.yaml");
      return CubeSchema.fromYaml(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Hierarchy timeAt(String level, String value) {
    return HierarchyFactory.create(atm.locate("time", level), value, atm);
  }

  private Hierarchy locAt(String level, String value) {
    return HierarchyFactory.create(atm.locate("location", level), value, atm);
  }

  private Hierarchy topicAt(String level, String value) {
    return HierarchyFactory.create(atm.locate("topic", level), value, atm);
  }

  /**
   * A stored context can be finer than the scope in one dimension and coarser in another (e.g., a
   * SIGMET indexed at (day, FIR) against a scope of (month, airport)). Such a context covers cells
   * inside the scope — (day, LOWW) rolls up to (day, LOVV) — so by KG-OLAP Definition 4 and the
   * propagation condition (Definition 3 (vi)) its knowledge belongs in the answer. The covering
   * resolution must therefore surface it.
   */
  @Test
  void mixedGranularityContextCoveringInScopeCellsIsResolved() {
    var inScope =
        Context.of(
            List.of(
                timeAt("day", "2025-01-15"),
                locAt("location", "LOWW"),
                topicAt("feature", "ApronElement")),
            atm);
    var mixed =
        Context.of(
            List.of(timeAt("day", "2025-01-15"), locAt("fir", "LOVV"), topicAt("family", "Apron")),
            atm);
    repo.upsertContext(atm, inScope);
    repo.upsertContext(atm, mixed);

    var scope =
        SliceDiceContext.of(List.of(timeAt("month", "2025-01"), locAt("location", "LOWW")), atm);

    assertThat(repo.getCoveringContexts(atm, scope))
        .as("mixed-granularity context covering in-scope cells must be visible to the query")
        .contains(mixed);
    assertThat(repo.getSpecificContexts(atm, scope))
        .as("the mixed context must not double as a specific context")
        .doesNotContain(mixed)
        .contains(inScope);
  }

  /**
   * A stored context equal to the scope in one dimension and coarser in another covers the whole
   * scope and must be resolved as a covering context (the Cassandra resolution accepts it via the
   * roll-up chain, which includes the coordinate itself; only the fully equal tuple is a specific
   * context instead). The in-memory stand-in must mirror that, or service tests built on it
   * under-test propagation.
   */
  @Test
  void contextEqualInOneDimensionAndCoarserInAnotherIsResolvedAsCovering() {
    var covering =
        Context.of(
            List.of(timeAt("month", "2025-01"), locAt("fir", "LOVV"), topicAt("family", "Apron")),
            atm);
    repo.upsertContext(atm, covering);

    var scope =
        SliceDiceContext.of(List.of(timeAt("month", "2025-01"), locAt("location", "LOWW")), atm);

    assertThat(repo.getCoveringContexts(atm, scope))
        .as("equal-in-time, coarser-in-location context covers the scope")
        .contains(covering);
  }
}
