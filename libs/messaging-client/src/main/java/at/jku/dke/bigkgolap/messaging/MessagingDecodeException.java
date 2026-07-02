package at.jku.dke.bigkgolap.messaging;

public final class MessagingDecodeException extends MessagingException {

  public MessagingDecodeException(String message, Throwable cause) {
    super(message, cause);
  }

  public MessagingDecodeException(String message) {
    super(message);
  }
}
