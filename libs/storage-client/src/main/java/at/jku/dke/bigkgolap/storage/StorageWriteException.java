package at.jku.dke.bigkgolap.storage;

public final class StorageWriteException extends StorageException {

  public StorageWriteException(String message) {
    super(message, null);
  }

  public StorageWriteException(String message, Throwable cause) {
    super(message, cause);
  }
}
