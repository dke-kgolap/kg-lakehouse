package at.jku.dke.bigkgolap.index;

public class IndexClientException extends RuntimeException {
  public IndexClientException(String message) {
    super(message);
  }

  public IndexClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
