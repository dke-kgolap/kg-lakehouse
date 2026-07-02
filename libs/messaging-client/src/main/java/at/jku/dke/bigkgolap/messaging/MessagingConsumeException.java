package at.jku.dke.bigkgolap.messaging;

public final class MessagingConsumeException extends MessagingException {

  public MessagingConsumeException(String message, Throwable cause) {
    super(message, cause);
  }

  public MessagingConsumeException(String message) {
    super(message);
  }
}
