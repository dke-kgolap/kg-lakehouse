package at.jku.dke.bigkgolap.model;

public final class InvalidCubeSchemaException extends DomainModelException {

  public InvalidCubeSchemaException(String message) {
    super(message);
  }

  public InvalidCubeSchemaException(String message, Throwable cause) {
    super(message, cause);
  }
}
