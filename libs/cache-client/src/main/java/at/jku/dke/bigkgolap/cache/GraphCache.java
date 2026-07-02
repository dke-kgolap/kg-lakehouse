package at.jku.dke.bigkgolap.cache;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.util.Arrays;
import java.util.Collection;

public interface GraphCache {

  /** Returns the cached graph, or {@code null} if not present. */
  CachedGraph loadGraph(String schemaId, String contextId, GraphRepresentation representation);

  void upsertGraph(
      String schemaId, String contextId, GraphRepresentation representation, byte[] data);

  /**
   * invalidation lists the representations to wipe. Source-data-changed implies all reps stale, so
   * callers typically pass all {@link GraphRepresentation} values. Each {@code (contextId,
   * representation)} pair becomes a flat {@code DEL} — no Redis SCAN.
   */
  void deleteCachedGraphs(
      String schemaId,
      Collection<String> contextIds,
      Collection<GraphRepresentation> representations);

  /** Convenience overload: deletes all representations for the given context IDs. */
  default void deleteCachedGraphs(String schemaId, Collection<String> contextIds) {
    deleteCachedGraphs(schemaId, contextIds, Arrays.asList(GraphRepresentation.values()));
  }

  void clear();
}
