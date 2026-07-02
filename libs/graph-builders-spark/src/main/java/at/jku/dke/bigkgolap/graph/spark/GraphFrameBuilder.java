package at.jku.dke.bigkgolap.graph.spark;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.TopologyStats;
import at.jku.dke.bigkgolap.graph.GraphFormats;
import at.jku.dke.bigkgolap.graph.lpg.LpgConventions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * GraphFrame implementation. Path B (retro): no in-process Spark runtime. Output is JSON Lines in
 * the GraphFrame schema, ready for {@code spark.read.json(...)} on the consumer side to materialise
 * a real {@code org.graphframes.GraphFrame}.
 *
 * <p>Conversion semantics share {@link LpgConventions} with {@code LpgGraphBuilder}.
 *
 * <p>Wire format (each line is one JSON object):
 *
 * <pre>
 *   {"_kind":"v","row":{"id":"...","label":"Foo","props":{"k":["v1","v2"]}}}
 *   {"_kind":"e","row":{"src":"...","dst":"...","label":"p","props":{}}}
 * </pre>
 *
 * The {@code _kind} discriminator is stripped on the consumer side:
 *
 * <pre>
 *   val all      = spark.read.json(path)
 *   val vertices = all.filter($"_kind" === "v").select($"row.*")
 *   val edges    = all.filter($"_kind" === "e").select($"row.*")
 *   GraphFrame(vertices, edges)
 * </pre>
 */
public class GraphFrameBuilder implements GraphBuilder {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private record TripleRecord(String subject, String predicate, String obj, boolean isLiteral) {}

  /** Vertex row in the GraphFrame schema. */
  public record VertexRow(String id, String label, Map<String, List<String>> props) {}

  /** Edge row in the GraphFrame schema. */
  public record EdgeRow(String src, String dst, String label, Map<String, List<String>> props) {}

  /** The built graph data: vertices + edges. */
  public record GraphFrameData(List<VertexRow> vertices, List<EdgeRow> edges) {}

  private final List<TripleRecord> triples = new ArrayList<>();
  private long tripleCounter = 0L;
  private GraphFrameData built = null;

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
    // drop the named-graph arg, mirroring RDF/LPG.
    addTriple(subject, predicate, obj, false, null, null);
  }

  @Override
  public Object build() {
    if (built != null) return built;

    Map<String, String> labels = collectLabels();
    Map<String, Map<String, List<String>>> vertexProps = new HashMap<>();
    List<EdgeRow> edges = new ArrayList<>();
    var touched = new HashSet<>(labels.keySet());

    for (var t : triples) {
      applyTriple(t, vertexProps, edges, touched);
    }

    List<VertexRow> vertexRows = new ArrayList<>(touched.size());
    for (var id : touched) {
      Map<String, List<String>> props = vertexProps.get(id);
      vertexRows.add(
          new VertexRow(
              id,
              labels.getOrDefault(id, LpgConventions.DEFAULT_VERTEX_LABEL),
              props != null ? props : Map.of()));
    }

    built = new GraphFrameData(vertexRows, List.copyOf(edges));
    return built;
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
      Map<String, Map<String, List<String>>> vertexProps,
      List<EdgeRow> edges,
      HashSet<String> touched) {
    if (LpgConventions.isRdfType(t.predicate())) {
      touched.add(t.subject());
      return;
    }
    touched.add(t.subject());
    if (t.isLiteral()) {
      String key = LpgConventions.extractLastSegment(t.predicate());
      vertexProps
          .computeIfAbsent(t.subject(), k -> new HashMap<>())
          .computeIfAbsent(key, k -> new ArrayList<>())
          .add(t.obj());
    } else {
      touched.add(t.obj());
      edges.add(
          new EdgeRow(
              t.subject(), t.obj(), LpgConventions.extractLastSegment(t.predicate()), Map.of()));
    }
  }

  @Override
  public byte[] serialize(String format) {
    if (!GraphFormats.GRAPHFRAME_JSON.equals(format)) {
      throw new IllegalArgumentException(
          "GraphFrameBuilder supports only %s (got '%s')"
              .formatted(GraphFormats.GRAPHFRAME_JSON, format));
    }
    var data = (GraphFrameData) build();
    var out = new StringBuilder();
    try {
      for (var v : data.vertices()) {
        out.append(MAPPER.writeValueAsString(Map.of("_kind", "v", "row", v)));
        out.append('\n');
      }
      for (var e : data.edges()) {
        out.append(MAPPER.writeValueAsString(Map.of("_kind", "e", "row", e)));
        out.append('\n');
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize GraphFrameData", ex);
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public long tripleCount() {
    return tripleCounter;
  }

  @Override
  public TopologyStats topologyStats() {
    var data = (GraphFrameData) build();
    return new TopologyStats(data.vertices().size(), data.edges().size());
  }
}
