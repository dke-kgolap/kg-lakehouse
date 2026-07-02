package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.model.MergeLevels;
import at.jku.dke.bigkgolap.model.SliceDiceContext;

public record ParsedQuery(
    SliceDiceContext sliceDice,
    MergeLevels mergeLevels,
    GraphRepresentation representation,
    String format,
    boolean reasoning) {

  public static final String DEFAULT_FORMAT = "application/n-quads";

  public ParsedQuery(SliceDiceContext sliceDice, MergeLevels mergeLevels) {
    this(sliceDice, mergeLevels, GraphRepresentation.RDF, DEFAULT_FORMAT, false);
  }

  public ParsedQuery(
      SliceDiceContext sliceDice,
      MergeLevels mergeLevels,
      GraphRepresentation representation,
      String format) {
    this(sliceDice, mergeLevels, representation, format, false);
  }

  /** Return a copy with a different representation and format. */
  public ParsedQuery copy(GraphRepresentation newRepresentation, String newFormat) {
    return new ParsedQuery(sliceDice, mergeLevels, newRepresentation, newFormat, reasoning);
  }

  /** Return a copy with reasoning (derived-knowledge inference) enabled or disabled. */
  public ParsedQuery withReasoning(boolean newReasoning) {
    return new ParsedQuery(sliceDice, mergeLevels, representation, format, newReasoning);
  }
}
