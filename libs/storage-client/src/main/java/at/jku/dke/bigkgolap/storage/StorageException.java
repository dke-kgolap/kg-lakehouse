package at.jku.dke.bigkgolap.storage;

public abstract sealed class StorageException extends RuntimeException
    permits StorageNotFoundException, StorageWriteException, StorageReadException {

  protected StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
