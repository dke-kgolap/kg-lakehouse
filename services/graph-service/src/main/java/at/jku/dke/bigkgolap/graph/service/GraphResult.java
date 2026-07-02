package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.cache.CachedGraph;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.util.Arrays;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * representation-aware result. {@code model} carries the live builder output for cache-miss paths
 * (RDF: Jena Model, LPG: TinkerGraph, GraphFrame: GraphFrameData); for cache-hit paths in
 * LPG/GraphFrame it stays null and the streaming path reads directly from {@code serialized}
 * (line-oriented per-rep format). RDF cache hits still deserialize Thrift → Model so per-request
 * graph-URI tagging in {@code streamQuads} works.
 */
public final class GraphResult {

  private static final int HASH_PRIME = 31;

  private final GraphRepresentation representation;
  private final Object model;
  private final byte[] serialized;
  private final long tripleCount;
  private final boolean fromCache;
  private final InProcessGraphCache.CachedRender render;

  public GraphResult(
      GraphRepresentation representation,
      Object model,
      byte[] serialized,
      long tripleCount,
      boolean fromCache,
      InProcessGraphCache.CachedRender render) {
    this.representation = representation;
    this.model = model;
    this.serialized = serialized;
    this.tripleCount = tripleCount;
    this.fromCache = fromCache;
    this.render = render;
  }

  public GraphRepresentation representation() {
    return representation;
  }

  public Object model() {
    return model;
  }

  public byte[] serialized() {
    return serialized;
  }

  public long tripleCount() {
    return tripleCount;
  }

  public boolean fromCache() {
    return fromCache;
  }

  public InProcessGraphCache.CachedRender render() {
    return render;
  }

  public static GraphResult fromRender(
      InProcessGraphCache.CachedRender render, GraphRepresentation representation) {
    return new GraphResult(representation, null, null, render.tripleCount(), true, render);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof GraphResult that)) return false;
    return representation == that.representation
        && tripleCount == that.tripleCount
        && fromCache == that.fromCache
        && Arrays.equals(serialized, that.serialized)
        && java.util.Objects.equals(model, that.model);
  }

  @Override
  public int hashCode() {
    int result = representation.hashCode();
    result = HASH_PRIME * result + Long.hashCode(tripleCount);
    result = HASH_PRIME * result + Boolean.hashCode(fromCache);
    result = HASH_PRIME * result + Arrays.hashCode(serialized);
    result = HASH_PRIME * result + (model != null ? model.hashCode() : 0);
    return result;
  }

  public static GraphResult fromCache(CachedGraph cached, GraphRepresentation representation) {
    return switch (representation) {
      case RDF -> rdfFromCache(cached);
      case LPG, GRAPH_FRAME ->
          new GraphResult(
              representation, null, cached.data(), countLines(cached.data()), true, null);
    };
  }

  private static GraphResult rdfFromCache(CachedGraph cached) {
    // N-Quads preserves the asserted (default) vs derived (named) module split across the cache
    // round-trip; a triple-only format would collapse the two modules.
    Dataset dataset = DatasetFactory.create();
    RDFDataMgr.read(dataset, new java.io.ByteArrayInputStream(cached.data()), Lang.NQUADS);
    return new GraphResult(
        GraphRepresentation.RDF, dataset, cached.data(), datasetSize(dataset), true, null);
  }

  private static long datasetSize(Dataset dataset) {
    long count = dataset.getDefaultModel().size();
    var names = dataset.listNames();
    while (names.hasNext()) {
      count += dataset.getNamedModel(names.next()).size();
    }
    return count;
  }

  private static long countLines(byte[] bytes) {
    long count = 0;
    for (byte b : bytes) {
      if (b == '\n') count++;
    }
    return count;
  }
}
