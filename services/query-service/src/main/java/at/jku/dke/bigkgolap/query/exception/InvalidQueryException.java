package at.jku.dke.bigkgolap.query.exception;

public class InvalidQueryException extends RuntimeException {

  public InvalidQueryException(String message) {
    super(message);
  }

  public InvalidQueryException(String message, Throwable cause) {
    super(message, cause);
  }
}
