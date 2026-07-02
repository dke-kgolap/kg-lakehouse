package at.jku.dke.bigkgolap.surface.api.dto;

import at.jku.dke.bigkgolap.model.GraphRepresentation;

/** Wire payload sent to query-service's {@code /query} endpoint. */
public record QueryRequest(
    String schemaId,
    String query,
    GraphRepresentation representation,
    String format,
    boolean reasoning) {

  public QueryRequest(String schemaId, String query, GraphRepresentation representation) {
    this(schemaId, query, representation, "application/n-quads", false);
  }
}
