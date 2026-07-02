package at.jku.dke.bigkgolap.storage;

public final class StorageNotFoundException extends StorageException {

  public StorageNotFoundException(String schemaId, String storedName) {
    super("No object stored at '%s/%s'".formatted(schemaId, storedName), null);
  }

  public StorageNotFoundException(String schemaId, String storedName, Throwable cause) {
    super("No object stored at '%s/%s'".formatted(schemaId, storedName), cause);
  }
}
