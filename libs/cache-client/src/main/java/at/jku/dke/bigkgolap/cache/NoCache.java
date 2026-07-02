package at.jku.dke.bigkgolap.cache;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.util.Collection;

/** No-op {@link GraphCache} implementation. All operations are safe no-ops. */
public final class NoCache implements GraphCache {

  public static final NoCache INSTANCE = new NoCache();

  private NoCache() {}

  @Override
  public CachedGraph loadGraph(
      String schemaId, String contextId, GraphRepresentation representation) {
    return null;
  }

  @Override
  public void upsertGraph(
      String schemaId, String contextId, GraphRepresentation representation, byte[] data) {
    // no-op
  }

  @Override
  public void deleteCachedGraphs(
      String schemaId,
      Collection<String> contextIds,
      Collection<GraphRepresentation> representations) {
    // no-op
  }

  @Override
  public void clear() {
    // no-op
  }
}
