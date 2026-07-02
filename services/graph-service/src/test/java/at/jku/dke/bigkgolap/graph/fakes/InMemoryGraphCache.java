package at.jku.dke.bigkgolap.graph.fakes;

import at.jku.dke.bigkgolap.cache.CachedGraph;
import at.jku.dke.bigkgolap.cache.GraphCache;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.time.Clock;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGraphCache implements GraphCache {

  public record Key(String schemaId, String contextId, GraphRepresentation representation) {}

  public final ConcurrentHashMap<Key, byte[]> storage = new ConcurrentHashMap<>();

  private final Clock clock;

  public InMemoryGraphCache() {
    this(Clock.systemUTC());
  }

  public InMemoryGraphCache(Clock clock) {
    this.clock = clock;
  }

  @Override
  public CachedGraph loadGraph(
      String schemaId, String contextId, GraphRepresentation representation) {
    byte[] data = storage.get(new Key(schemaId, contextId, representation));
    return data != null ? new CachedGraph(data, clock.instant()) : null;
  }

  @Override
  public void upsertGraph(
      String schemaId, String contextId, GraphRepresentation representation, byte[] data) {
    storage.put(new Key(schemaId, contextId, representation), data);
  }

  @Override
  public void deleteCachedGraphs(
      String schemaId,
      Collection<String> contextIds,
      Collection<GraphRepresentation> representations) {
    for (String id : contextIds) {
      for (GraphRepresentation repr : representations) {
        storage.remove(new Key(schemaId, id, repr));
      }
    }
  }

  @Override
  public void clear() {
    storage.clear();
  }
}
