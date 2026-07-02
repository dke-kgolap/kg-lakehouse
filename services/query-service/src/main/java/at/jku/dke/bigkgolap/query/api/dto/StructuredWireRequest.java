package at.jku.dke.bigkgolap.query.api.dto;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record StructuredWireRequest(
    String schemaId,
    Map<String, Map<String, String>> select,
    Map<String, String> rollup,
    GraphRepresentation representation,
    String format,
    boolean reasoning) {

  @JsonCreator
  public StructuredWireRequest(
      @JsonProperty("schemaId") String schemaId,
      @JsonProperty("select") Map<String, Map<String, String>> select,
      @JsonProperty("rollup") Map<String, String> rollup,
      @JsonProperty("representation") GraphRepresentation representation,
      @JsonProperty("format") String format,
      @JsonProperty("reasoning") boolean reasoning) {
    this.schemaId = schemaId;
    this.select = select != null ? select : Map.of();
    this.rollup = rollup != null ? rollup : Map.of();
    this.representation = representation != null ? representation : GraphRepresentation.RDF;
    this.format = format != null ? format : "application/n-quads";
    this.reasoning = reasoning;
  }

  public StructuredWireRequest(String schemaId, Map<String, Map<String, String>> select) {
    this(schemaId, select, Map.of(), GraphRepresentation.RDF, "application/n-quads", false);
  }
}
