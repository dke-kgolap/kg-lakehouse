package at.jku.dke.bigkgolap.storage;

import at.jku.dke.bigkgolap.storage.internal.ObjectKeys;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalStorageService implements StorageService {

  private final Path root;

  public LocalStorageService(Path root) {
    this.root = root;
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new StorageWriteException("Failed to create storage root at " + root, e);
    }
  }

  @Override
  public void store(String schemaId, String storedName, InputStream input, long sizeBytes) {
    var target = resolve(schemaId, storedName);
    try {
      Files.createDirectories(target.getParent());
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new StorageWriteException("Failed to store '%s/%s'".formatted(schemaId, storedName), e);
    }
  }

  @Override
  public InputStream load(String schemaId, String storedName) {
    var source = resolve(schemaId, storedName);
    try {
      return Files.newInputStream(source);
    } catch (NoSuchFileException e) {
      throw new StorageNotFoundException(schemaId, storedName, e);
    } catch (IOException e) {
      throw new StorageReadException("Failed to load '%s/%s'".formatted(schemaId, storedName), e);
    }
  }

  @Override
  public boolean exists(String schemaId, String storedName) {
    return Files.isRegularFile(resolve(schemaId, storedName));
  }

  @Override
  public void delete(String schemaId, String storedName) {
    try {
      Files.deleteIfExists(resolve(schemaId, storedName));
    } catch (IOException e) {
      throw new StorageWriteException(
          "Failed to delete '%s/%s'".formatted(schemaId, storedName), e);
    }
  }

  @Override
  public void deleteAllForSchema(String schemaId) {
    ObjectKeys.validate(schemaId);
    var schemaRoot = root.resolve(schemaId);
    if (!Files.exists(schemaRoot) || !Files.isDirectory(schemaRoot)) {
      return;
    }
    try {
      deleteRecursively(schemaRoot);
    } catch (IOException e) {
      throw new StorageWriteException("Failed to delete schema '%s'".formatted(schemaId), e);
    }
  }

  @Override
  public void clearAll() {
    try {
      if (Files.isDirectory(root)) {
        try (var stream = Files.list(root)) {
          for (var entry : stream.toList()) {
            deleteRecursively(entry);
          }
        }
      }
    } catch (IOException e) {
      throw new StorageWriteException("Failed to clear storage at " + root, e);
    }
  }

  private Path resolve(String schemaId, String storedName) {
    ObjectKeys.validate(schemaId);
    if (storedName == null || storedName.isBlank()) {
      throw new IllegalArgumentException("storedName must not be blank");
    }
    if (storedName.contains("/")) {
      throw new IllegalArgumentException("storedName must not contain '/'");
    }
    return root.resolve(schemaId).resolve(storedName);
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (var entries = Files.list(path)) {
        for (var entry : entries.toList()) {
          deleteRecursively(entry);
        }
      }
    }
    Files.deleteIfExists(path);
  }
}
