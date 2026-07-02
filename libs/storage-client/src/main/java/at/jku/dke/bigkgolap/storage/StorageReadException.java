package at.jku.dke.bigkgolap.storage;

public final class StorageReadException extends StorageException {

  public StorageReadException(String message) {
    super(message, null);
  }

  public StorageReadException(String message, Throwable cause) {
    super(message, cause);
  }
}
