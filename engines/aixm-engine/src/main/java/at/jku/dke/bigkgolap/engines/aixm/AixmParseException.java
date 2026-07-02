package at.jku.dke.bigkgolap.engines.aixm;

public class AixmParseException extends RuntimeException {

  public AixmParseException(String message) {
    super(message);
  }

  public AixmParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
