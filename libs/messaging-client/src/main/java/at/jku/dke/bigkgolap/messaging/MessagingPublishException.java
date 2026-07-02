package at.jku.dke.bigkgolap.messaging;

public final class MessagingPublishException extends MessagingException {

  public MessagingPublishException(String message, Throwable cause) {
    super(message, cause);
  }

  public MessagingPublishException(String message) {
    super(message);
  }
}
