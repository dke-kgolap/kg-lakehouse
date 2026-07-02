package at.jku.dke.bigkgolap.graph;

import at.jku.dke.bigkgolap.model.GraphRepresentation;

public class UnsupportedRepresentationException extends RuntimeException {

  public UnsupportedRepresentationException(GraphRepresentation representation) {
    super("Representation %s not implemented.".formatted(representation));
  }

  public UnsupportedRepresentationException(GraphRepresentation representation, String message) {
    super(message);
  }
}
