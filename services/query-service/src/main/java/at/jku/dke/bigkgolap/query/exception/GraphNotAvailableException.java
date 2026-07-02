package at.jku.dke.bigkgolap.query.exception;

public class GraphNotAvailableException extends RuntimeException {

  public GraphNotAvailableException(String message) {
    super(message);
  }

  public GraphNotAvailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
