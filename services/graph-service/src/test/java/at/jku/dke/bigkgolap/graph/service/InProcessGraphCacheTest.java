package at.jku.dke.bigkgolap.graph.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class InProcessGraphCacheTest {
  private InProcessGraphCache.CachedRender render(String body) {
    return new InProcessGraphCache.CachedRender(List.of(body), List.of(), 1);
  }

  @Test
  void putThenGetReturnsValueAndCountsHit() {
    var c = new InProcessGraphCache(true, 16, new SimpleMeterRegistry());
    var k = new InProcessGraphCache.Key("atm", "c1", GraphRepresentation.RDF);
    assertThat(c.get(k)).isNull();
    c.put(k, render("a"));
    assertThat(c.get(k)).isNotNull();
    assertThat(c.get(k).assertedBodies()).containsExactly("a");
  }

  @Test
  void disabledNeverStores() {
    var c = new InProcessGraphCache(false, 16, new SimpleMeterRegistry());
    var k = new InProcessGraphCache.Key("atm", "c1", GraphRepresentation.RDF);
    c.put(k, render("a"));
    assertThat(c.get(k)).isNull();
  }

  @Test
  void evictsBeyondMaxEntries() {
    var c = new InProcessGraphCache(true, 2, new SimpleMeterRegistry());
    var k1 = new InProcessGraphCache.Key("atm", "c1", GraphRepresentation.RDF);
    var k2 = new InProcessGraphCache.Key("atm", "c2", GraphRepresentation.RDF);
    var k3 = new InProcessGraphCache.Key("atm", "c3", GraphRepresentation.RDF);
    c.put(k1, render("1"));
    c.put(k2, render("2"));
    c.get(k1); // touch k1 so k2 is the eldest
    c.put(k3, render("3"));
    assertThat(c.get(k2)).isNull(); // evicted
    assertThat(c.get(k1)).isNotNull();
    assertThat(c.get(k3)).isNotNull();
  }

  @Test
  void invalidateRemovesNamedContexts() {
    var c = new InProcessGraphCache(true, 16, new SimpleMeterRegistry());
    var k = new InProcessGraphCache.Key("atm", "c1", GraphRepresentation.RDF);
    c.put(k, render("a"));
    c.invalidate("atm", List.of("c1"), List.of(GraphRepresentation.RDF));
    assertThat(c.get(k)).isNull();
  }
}
