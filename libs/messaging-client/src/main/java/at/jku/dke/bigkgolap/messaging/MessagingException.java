package at.jku.dke.bigkgolap.messaging;

public abstract sealed class MessagingException extends RuntimeException
    permits MessagingPublishException, MessagingConsumeException, MessagingDecodeException {

  protected MessagingException(String message, Throwable cause) {
    super(message, cause);
  }

  protected MessagingException(String message) {
    super(message, null);
  }
}
