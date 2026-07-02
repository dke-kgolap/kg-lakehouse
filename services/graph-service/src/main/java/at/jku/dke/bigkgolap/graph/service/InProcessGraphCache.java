package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, per-pod L1 cache of parse-free graph renders, in front of the shared Redis (L2) cache.
 * An L1 hit avoids both the Redis round-trip and the Jena N-Quads re-parse that every L2 hit pays.
 * Access-order LRU; thread-safe via a synchronized map; values are immutable.
 */
public class InProcessGraphCache {

  /** Cache key: one graph per (schema, context, representation). */
  public record Key(String schemaId, String contextId, GraphRepresentation representation) {}

  /**
   * Parse-free RDF render: the asserted/derived N-Triple bodies ({@code S P O}, no graph column),
   * recomposed per request via {@link at.jku.dke.bigkgolap.graph.NQuadWriter#composeQuad}.
   */
  public record CachedRender(
      List<String> assertedBodies, List<String> derivedBodies, long tripleCount) {
    public CachedRender {
      assertedBodies = List.copyOf(assertedBodies);
      derivedBodies = List.copyOf(derivedBodies);
    }
  }

  private final boolean enabled;
  private final Map<Key, CachedRender> map;
  private final Counter hits;
  private final Counter misses;

  public InProcessGraphCache(boolean enabled, int maxEntries, MeterRegistry registry) {
    this.enabled = enabled;
    int cap = Math.max(1, maxEntries);
    this.map =
        java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<Key, CachedRender> eldest) {
                return size() > cap;
              }
            });
    this.hits = Counter.builder("lakehouse.graph.l1").tag("result", "hit").register(registry);
    this.misses = Counter.builder("lakehouse.graph.l1").tag("result", "miss").register(registry);
  }

  public CachedRender get(Key key) {
    if (!enabled) {
      return null;
    }
    CachedRender v = map.get(key);
    (v != null ? hits : misses).increment();
    return v;
  }

  public void put(Key key, CachedRender value) {
    if (enabled) {
      map.put(key, value);
    }
  }

  public void invalidate(
      String schemaId, Collection<String> contextIds, Collection<GraphRepresentation> reps) {
    for (String ctx : contextIds) {
      for (GraphRepresentation rep : reps) {
        map.remove(new Key(schemaId, ctx, rep));
      }
    }
  }

  public void clear() {
    map.clear();
  }
}
