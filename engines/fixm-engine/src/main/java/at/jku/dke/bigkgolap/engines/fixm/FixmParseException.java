package at.jku.dke.bigkgolap.engines.fixm;

public class FixmParseException extends RuntimeException {

  public FixmParseException(String message) {
    super(message);
  }

  public FixmParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
