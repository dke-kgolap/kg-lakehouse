package at.jku.dke.bigkgolap.storage;

import java.io.InputStream;

public interface StorageService {

  void store(String schemaId, String storedName, InputStream input, long sizeBytes);

  InputStream load(String schemaId, String storedName);

  boolean exists(String schemaId, String storedName);

  void delete(String schemaId, String storedName);

  void deleteAllForSchema(String schemaId);

  void clearAll();
}
