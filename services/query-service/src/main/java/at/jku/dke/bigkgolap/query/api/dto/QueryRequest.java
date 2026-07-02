package at.jku.dke.bigkgolap.query.api.dto;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record QueryRequest(
    String schemaId,
    String query,
    GraphRepresentation representation,
    String format,
    boolean reasoning) {

  @JsonCreator
  public QueryRequest(
      @JsonProperty("schemaId") String schemaId,
      @JsonProperty("query") String query,
      @JsonProperty("representation") GraphRepresentation representation,
      @JsonProperty("format") String format,
      @JsonProperty("reasoning") boolean reasoning) {
    this.schemaId = schemaId;
    this.query = query;
    this.representation = representation != null ? representation : GraphRepresentation.RDF;
    this.format = format != null ? format : "application/n-quads";
    this.reasoning = reasoning;
  }

  public QueryRequest(String schemaId, String query) {
    this(schemaId, query, GraphRepresentation.RDF, "application/n-quads", false);
  }
}
