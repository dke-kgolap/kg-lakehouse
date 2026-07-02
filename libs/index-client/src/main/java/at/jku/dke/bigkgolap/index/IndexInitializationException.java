package at.jku.dke.bigkgolap.index;

public class IndexInitializationException extends IndexClientException {
  public IndexInitializationException(String message) {
    super(message);
  }

  public IndexInitializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
