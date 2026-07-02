package at.jku.dke.bigkgolap.surface.api.dto;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.util.Map;

/** Surface-side request body for the structured form ({@code POST /query/structured}). */
public record StructuredQueryRequest(
    Map<String, Map<String, String>> select,
    Map<String, String> rollup,
    GraphRepresentation representation,
    String format,
    boolean reasoning) {

  public StructuredQueryRequest() {
    this(Map.of(), Map.of(), GraphRepresentation.RDF, "application/n-quads", false);
  }
}
