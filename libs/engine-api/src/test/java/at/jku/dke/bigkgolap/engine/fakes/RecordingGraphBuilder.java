package at.jku.dke.bigkgolap.engine.fakes;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RecordingGraphBuilder implements GraphBuilder {

  public record Triple(
      String subject,
      String predicate,
      String obj,
      boolean isLiteral,
      String datatype,
      String lang,
      String graph) {

    /**
     * Canonical constructor — all nullable params allowed, subject/predicate/obj must not be null.
     */
    public Triple {
      Objects.requireNonNull(subject, "subject");
      Objects.requireNonNull(predicate, "predicate");
      Objects.requireNonNull(obj, "obj");
    }
  }

  private final List<Triple> triples = new ArrayList<>();

  @Override
  public void addTriple(
      String subject,
      String predicate,
      String obj,
      boolean isLiteral,
      String datatype,
      String lang) {
    triples.add(new Triple(subject, predicate, obj, isLiteral, datatype, lang, null));
  }

  @Override
  public void addTriple(String subject, String predicate, String obj, String graph) {
    triples.add(new Triple(subject, predicate, obj, false, null, null, graph));
  }

  @Override
  public Object build() {
    return List.copyOf(triples);
  }

  @Override
  public byte[] serialize(String format) {
    if (!format.equalsIgnoreCase("text/plain")) {
      throw new IllegalArgumentException(
          "RecordingGraphBuilder only supports 'text/plain' (got '%s')".formatted(format));
    }
    var sb = new StringBuilder();
    for (int i = 0; i < triples.size(); i++) {
      var t = triples.get(i);
      if (i > 0) sb.append('\n');
      sb.append(t.subject()).append(' ').append(t.predicate()).append(' ').append(t.obj());
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public long tripleCount() {
    return triples.size();
  }

  public List<Triple> captured() {
    return List.copyOf(triples);
  }
}
