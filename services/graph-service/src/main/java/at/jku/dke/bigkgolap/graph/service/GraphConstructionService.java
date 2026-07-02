package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.cache.GraphCache;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class GraphConstructionService {

  private static final String CACHE_HIT_METRIC = "lakehouse.query.cache.hit";

  private final GraphCache cache;
  private final FileLoaderService loader;
  private final MeterRegistry meterRegistry;
  private final InProcessGraphCache l1;

  public GraphConstructionService(
      GraphCache cache,
      FileLoaderService loader,
      MeterRegistry meterRegistry,
      InProcessGraphCache l1) {
    this.cache = cache;
    this.loader = loader;
    this.meterRegistry = meterRegistry;
    this.l1 = l1;
  }

  public GraphResult buildGraph(
      String schemaId, String contextId, GraphRepresentation representation) {
    var key = new InProcessGraphCache.Key(schemaId, contextId, representation);
    if (representation == GraphRepresentation.RDF) {
      var render = l1.get(key);
      if (render != null) {
        recordHit("hit", schemaId, representation);
        return GraphResult.fromRender(render, representation);
      }
    }
    var cached = cache.loadGraph(schemaId, contextId, representation);
    if (cached != null) {
      recordHit("hit", schemaId, representation);
      var result = GraphResult.fromCache(cached, representation);
      promote(key, result);
      return result;
    }
    recordHit("miss", schemaId, representation);
    GraphResult result = loader.loadAndBuild(schemaId, contextId, representation);
    cache.upsertGraph(schemaId, contextId, representation, result.serialized());
    promote(key, result);
    return result;
  }

  public int clearCache(String schemaId, Collection<String> contextIds) {
    if (contextIds.isEmpty()) {
      cache.clear();
      l1.clear();
    } else {
      cache.deleteCachedGraphs(schemaId, contextIds, Arrays.asList(GraphRepresentation.values()));
      l1.invalidate(schemaId, contextIds, Arrays.asList(GraphRepresentation.values()));
    }
    return contextIds.size();
  }

  /** Renders RDF results into the parse-free L1 form (no-op for LPG/GraphFrame for now). */
  private void promote(InProcessGraphCache.Key key, GraphResult result) {
    if (result.representation() != GraphRepresentation.RDF) {
      return;
    }
    if (!(result.model() instanceof org.apache.jena.query.Dataset dataset)) {
      return; // already render-backed (no live model) — nothing to promote
    }
    var asserted = renderGraph(dataset.getDefaultModel().getGraph());
    var derived =
        renderGraph(
            dataset
                .getNamedModel(at.jku.dke.bigkgolap.graph.RdfGraphBuilder.DERIVED_GRAPH)
                .getGraph());
    l1.put(
        key,
        new InProcessGraphCache.CachedRender(
            asserted, derived, (long) asserted.size() + derived.size()));
  }

  private static java.util.List<String> renderGraph(org.apache.jena.graph.Graph graph) {
    java.util.List<String> bodies = new java.util.ArrayList<>();
    var it = graph.find();
    try {
      while (it.hasNext()) {
        bodies.add(at.jku.dke.bigkgolap.graph.NQuadWriter.writeBody(it.next()));
      }
    } finally {
      it.close();
    }
    return bodies;
  }

  private void recordHit(String result, String schemaId, GraphRepresentation representation) {
    meterRegistry
        .counter(
            CACHE_HIT_METRIC,
            "result",
            result,
            "schema",
            schemaId,
            "representation",
            representation.name())
        .increment();
  }
}
