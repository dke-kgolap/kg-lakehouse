package at.jku.dke.bigkgolap.index;

import at.jku.dke.bigkgolap.index.support.CassandraTestBase;
import at.jku.dke.bigkgolap.index.support.Fixtures;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CassandraIndexRepositoryIT extends CassandraTestBase {

  private final CubeSchema atm = Fixtures.atmSchema();

  private Context ctx(Hierarchy time, Hierarchy loc, Hierarchy topic) {
    return Context.of(List.of(time, loc, topic), atm);
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

  private final Context c1 =
      ctx(
          timeAt("day", "2025-01-15"),
          locAt("location", "LOWW"),
          topicAt("feature", "ApronElement"));
  private final Context c2 =
      ctx(
          timeAt("day", "2025-02-15"),
          locAt("location", "LOWW"),
          topicAt("feature", "ApronElement"));
  private final Context c3 =
      ctx(
          timeAt("day", "2025-01-15"),
          locAt("location", "LOWS"),
          topicAt("feature", "ApronElement"));
  private final Context c4 =
      ctx(timeAt("month", "2025-01"), locAt("fir", "LOVV"), topicAt("family", "Apron"));
  private final Context c5 =
      ctx(
          timeAt("year", "2025"),
          locAt("territory", "Austria"),
          topicAt("category", "AirportHeliport"));

  private void seedAll() {
    for (var c : List.of(c1, c2, c3, c4, c5)) repo.upsertContext(atm, c);
  }

  // ------------------------------------------------------------------
  // 8.3.1 Hierarchy / context upsert
  // ------------------------------------------------------------------

  @Test
  void upsertContextWritesOneRowPerDimensionAndOneContextRow() {
    repo.upsertContext(atm, c1);
    var stats = repo.getStats(atm.id());
    Assertions.assertThat(stats.totalContexts()).isEqualTo(1L);
    Assertions.assertThat(stats.totalHierarchies()).isEqualTo(3L);
  }

  @Test
  void upsertingTheSameContextTwiceKeepsContextIdsSetAtSize1() {
    repo.upsertContext(atm, c1);
    repo.upsertContext(atm, c1);
    var specific = repo.getSpecificContexts(atm, SliceDiceContext.empty());
    Assertions.assertThat(specific).containsExactly(c1);
  }

  // ------------------------------------------------------------------
  // 8.3.2 Slice/dice
  // ------------------------------------------------------------------

  @Test
  void getSpecificContextsWithEmptySliceDiceReturnsAllStoredContexts() {
    seedAll();
    var all = repo.getSpecificContexts(atm, SliceDiceContext.empty());
    Assertions.assertThat(all).containsExactlyInAnyOrder(c1, c2, c3, c4, c5);
  }

  @Test
  void getSpecificContextsWithFirConstraintMatchesContextsAtFirOrDeeper() {
    seedAll();
    var sd = SliceDiceContext.of(List.of(locAt("fir", "LOVV")), atm);
    var matched = repo.getSpecificContexts(atm, sd);
    Assertions.assertThat(matched).containsExactlyInAnyOrder(c1, c2, c3, c4);
  }

  @Test
  void getSpecificContextsWithDayConstraintMatchesContextsOnThatDay() {
    seedAll();
    var sd = SliceDiceContext.of(List.of(timeAt("day", "2025-01-15")), atm);
    var matched = repo.getSpecificContexts(atm, sd);
    Assertions.assertThat(matched).containsExactlyInAnyOrder(c1, c3);
  }

  @Test
  void getSpecificContextsWithIntersectingDayAndLocationConstraintsPicksOne() {
    seedAll();
    var sd =
        SliceDiceContext.of(List.of(timeAt("day", "2025-01-15"), locAt("location", "LOWW")), atm);
    var matched = repo.getSpecificContexts(atm, sd);
    Assertions.assertThat(matched).containsExactly(c1);
  }

  @Test
  void getSpecificContextsWithNonExistentValueReturnsEmpty() {
    seedAll();
    var nonExistent = HierarchyFactory.create(atm.locate("location", "territory"), "Austria", atm);
    var ghostFir =
        Hierarchy.of(
            List.of(
                new Member(atm.locate("location", "territory"), "Austria"),
                new Member(atm.locate("location", "fir"), "ZZZZ")));
    var sd = SliceDiceContext.of(List.of(ghostFir), atm);
    Assertions.assertThat(repo.getSpecificContexts(atm, sd)).isEmpty();
    Assertions.assertThat(nonExistent.dimension()).isEqualTo("location");
  }

  // ------------------------------------------------------------------
  // 8.3.3 Rollup (general contexts)
  // ------------------------------------------------------------------

  @Test
  void getCoveringContextsWalksUpRollupChainsAndExcludesTheSliceDiceItself() {
    seedAll();
    var sd =
        SliceDiceContext.of(List.of(timeAt("day", "2025-01-15"), locAt("location", "LOWW")), atm);
    var covering = repo.getCoveringContexts(atm, sd);
    Assertions.assertThat(covering).containsExactlyInAnyOrder(c4, c5);
  }

  @Test
  void getCoveringContextsReturnsEmptyForAnEmptySliceDice() {
    seedAll();
    Assertions.assertThat(repo.getCoveringContexts(atm, SliceDiceContext.empty())).isEmpty();
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
        ctx(
            timeAt("day", "2025-01-15"),
            locAt("location", "LOWW"),
            topicAt("feature", "ApronElement"));
    var mixed = ctx(timeAt("day", "2025-01-15"), locAt("fir", "LOVV"), topicAt("family", "Apron"));
    repo.upsertContext(atm, inScope);
    repo.upsertContext(atm, mixed);

    var scope =
        SliceDiceContext.of(List.of(timeAt("month", "2025-01"), locAt("location", "LOWW")), atm);

    Assertions.assertThat(repo.getCoveringContexts(atm, scope))
        .as("mixed-granularity context covering in-scope cells must be visible to the query")
        .contains(mixed);
    Assertions.assertThat(repo.getSpecificContexts(atm, scope))
        .as("the mixed context must not double as a specific context")
        .doesNotContain(mixed)
        .contains(inScope);
  }

  // ------------------------------------------------------------------
  // 8.3.4 Multi-schema isolation
  // ------------------------------------------------------------------

  @Test
  void schemasAreIsolatedBySchemaId() {
    var weather = Fixtures.weatherSchema();
    repo.upsertContext(atm, c1);
    var weatherCtx =
        Context.of(
            List.of(
                HierarchyFactory.create(weather.locate("time", "month"), "2025-01", weather),
                HierarchyFactory.create(weather.locate("station", "station"), "LOWW", weather)),
            weather);
    repo.upsertContext(weather, weatherCtx);
    Assertions.assertThat(repo.getStats(atm.id()).totalContexts()).isEqualTo(1L);
    Assertions.assertThat(repo.getStats(weather.id()).totalContexts()).isEqualTo(1L);
    var atmAll = repo.getSpecificContexts(atm, SliceDiceContext.empty());
    Assertions.assertThat(atmAll).containsExactly(c1);
  }

  // ------------------------------------------------------------------
  // 8.3.5 Files
  // ------------------------------------------------------------------

  @Test
  void upsertFileAndGetFilesForContextRoundTrip() {
    repo.upsertContext(atm, c1);
    repo.upsertFile(atm.id(), c1.id(), "stored-1.xml", "aixm");
    repo.upsertFile(atm.id(), c1.id(), "stored-2.xml", "aixm");
    var files = repo.getFilesForContext(atm.id(), c1.id());
    Assertions.assertThat(files)
        .containsExactlyInAnyOrder(
            new LakehouseFile("stored-1.xml", "aixm"), new LakehouseFile("stored-2.xml", "aixm"));
  }

  @Test
  void getFilesForContextReturnsEmptyForAnUnknownContext() {
    Assertions.assertThat(repo.getFilesForContext(atm.id(), "deadbeef")).isEmpty();
  }

  // ------------------------------------------------------------------
  // 8.3.6 Logs
  // ------------------------------------------------------------------

  @Test
  void ingestionLogRoundTripWithAndWithoutSchemaFilter() {
    var now = Instant.parse("2026-04-26T10:00:00Z");
    var entry =
        new IngestionLogEntry(UUID.randomUUID(), atm.id(), "f.xml", "aixm", 11, 22, 33, 1, now);
    repo.logIngestion(entry);
    Assertions.assertThat(repo.getIngestionLogs(atm.id(), 10)).containsExactly(entry);
    Assertions.assertThat(repo.getIngestionLogs(null, 10)).contains(entry);
  }

  @Test
  void queryLogRoundTrip() {
    var now = Instant.parse("2026-04-26T10:00:00Z");
    var entry =
        new QueryLogEntry(
            UUID.randomUUID(), atm.id(), "SELECT *", 1, 2, 3, 4, 5, 6, 7, 8, true, null, now);
    repo.logQuery(entry);
    Assertions.assertThat(repo.getQueryLogs(atm.id(), 10)).containsExactly(entry);
  }

  // ------------------------------------------------------------------
  // 8.3.7 Reset
  // ------------------------------------------------------------------

  @Test
  void resetTruncatesAllTables() {
    seedAll();
    repo.upsertFile(atm.id(), c1.id(), "stored.xml", "aixm");
    repo.reset();
    var stats = repo.getStats(atm.id());
    Assertions.assertThat(stats.totalContexts()).isZero();
    Assertions.assertThat(stats.totalHierarchies()).isZero();
    Assertions.assertThat(repo.getFilesForContext(atm.id(), c1.id())).isEmpty();
  }
}
