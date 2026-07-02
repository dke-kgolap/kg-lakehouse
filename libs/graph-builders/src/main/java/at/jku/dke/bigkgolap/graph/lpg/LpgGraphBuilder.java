package at.jku.dke.bigkgolap.graph.lpg;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.TopologyStats;
import at.jku.dke.bigkgolap.graph.GraphFormats;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONVersion;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

/**
 * LPG implementation. Buffers triples, materialises a {@code TinkerGraph} lazily on first {@code
 * build()} call. The two-pass flow lets {@code rdf:type} triples that arrive <em>after</em> edges
 * (AIXM's parent-emits-edge-then-child-type pattern) still set vertex labels correctly — TinkerPop
 * vertex labels are immutable after creation.
 *
 * <p>Conversion semantics live in {@link LpgConventions}.
 */
public class LpgGraphBuilder implements GraphBuilder {

  private record TripleRecord(String subject, String predicate, String obj, boolean isLiteral) {}

  private final List<TripleRecord> triples = new ArrayList<>();
  private long tripleCounter = 0L;
  private TinkerGraph built = null;

  @Override
  public void addTriple(
      String subject,
      String predicate,
      String obj,
      boolean isLiteral,
      String datatype,
      String lang) {
    triples.add(new TripleRecord(subject, predicate, obj, isLiteral));
    tripleCounter++;
  }

  @Override
  public void addTriple(String subject, String predicate, String obj, String graph) {
    // drop the named-graph arg, mirroring RDF.
    addTriple(subject, predicate, obj, false, null, null);
  }

  @Override
  public Object build() {
    if (built != null) {
      return built;
    }
    var graph = TinkerGraph.open();
    var labels = collectLabels();
    var vertexById = new HashMap<String, Vertex>(labels.size() * 2);
    for (var t : triples) {
      applyTriple(t, graph, labels, vertexById);
    }
    built = graph;
    return graph;
  }

  private Map<String, String> collectLabels() {
    var labels = new HashMap<String, String>();
    for (var t : triples) {
      if (LpgConventions.isRdfType(t.predicate())) {
        labels.putIfAbsent(t.subject(), LpgConventions.extractLabel(t.obj(), t.isLiteral()));
      }
    }
    return labels;
  }

  private void applyTriple(
      TripleRecord t,
      TinkerGraph graph,
      Map<String, String> labels,
      Map<String, Vertex> vertexById) {
    if (LpgConventions.isRdfType(t.predicate())) {
      vertexFor(t.subject(), graph, labels, vertexById);
      return;
    }
    var source = vertexFor(t.subject(), graph, labels, vertexById);
    if (t.isLiteral()) {
      source.property(
          VertexProperty.Cardinality.list,
          LpgConventions.extractLastSegment(t.predicate()),
          t.obj());
    } else {
      var target = vertexFor(t.obj(), graph, labels, vertexById);
      source.addEdge(LpgConventions.extractLastSegment(t.predicate()), target);
    }
  }

  private Vertex vertexFor(
      String id, TinkerGraph graph, Map<String, String> labels, Map<String, Vertex> vertexById) {
    return vertexById.computeIfAbsent(
        id,
        key -> {
          var label = labels.getOrDefault(key, LpgConventions.DEFAULT_VERTEX_LABEL);
          return graph.addVertex(T.label, label, T.id, key);
        });
  }

  @Override
  public byte[] serialize(String format) {
    if (!GraphFormats.GRAPHSON_V3.equals(format)) {
      throw new IllegalArgumentException(
          "LpgGraphBuilder supports only %s (got '%s')"
              .formatted(GraphFormats.GRAPHSON_V3, format));
    }
    // wire format: one GraphSON v3 element per line so cache hits stream by
    // newline split instead of needing a full TinkerGraph reconstruction.
    var graph = (Graph) build();
    var mapper = GraphSONMapper.build().version(GraphSONVersion.V3_0).create();
    var writer = GraphSONWriter.build().mapper(mapper).create();
    var out = new ByteArrayOutputStream();
    graph
        .vertices()
        .forEachRemaining(
            v -> {
              var perElement = new ByteArrayOutputStream();
              try {
                writer.writeVertex(perElement, v);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              out.writeBytes(perElement.toByteArray());
              out.write('\n');
            });
    graph
        .edges()
        .forEachRemaining(
            e -> {
              var perElement = new ByteArrayOutputStream();
              try {
                writer.writeEdge(perElement, e);
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
              out.writeBytes(perElement.toByteArray());
              out.write('\n');
            });
    return out.toByteArray();
  }

  @Override
  public long tripleCount() {
    return tripleCounter;
  }

  @Override
  public TopologyStats topologyStats() {
    var graph = (Graph) build();
    long vertices = 0L;
    long edges = 0L;
    var vIter = graph.vertices();
    while (vIter.hasNext()) {
      vIter.next();
      vertices++;
    }
    var eIter = graph.edges();
    while (eIter.hasNext()) {
      eIter.next();
      edges++;
    }
    return new TopologyStats(vertices, edges);
  }
}
