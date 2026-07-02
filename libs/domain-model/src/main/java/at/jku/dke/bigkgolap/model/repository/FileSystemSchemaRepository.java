package at.jku.dke.bigkgolap.model.repository;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.InvalidCubeSchemaException;
import at.jku.dke.bigkgolap.model.SchemaAlreadyRegisteredException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class FileSystemSchemaRepository implements SchemaRepository {

  private final Path root;
  private final ConcurrentHashMap<String, CubeSchema> cache = new ConcurrentHashMap<>();

  public FileSystemSchemaRepository(Path root) {
    try {
      if (!Files.exists(root)) {
        Files.createDirectories(root);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException(
          "FileSystemSchemaRepository root must be a directory: " + root);
    }
    this.root = root;
  }

  @Override
  public SortedSet<String> list() {
    var ids = new TreeSet<String>();
    ids.addAll(cache.keySet());
    try (var stream = Files.list(root)) {
      stream.forEach(
          p -> {
            String fn = p.getFileName().toString();
            if (fn.endsWith(".yaml")) {
              ids.add(fn.substring(0, fn.length() - 5));
            } else if (fn.endsWith(".yml")) {
              ids.add(fn.substring(0, fn.length() - 4));
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return ids;
  }

  @Override
  public CubeSchema get(String id) {
    CubeSchema cached = cache.get(id);
    if (cached != null) return cached;
    Path file = locate(id);
    if (file == null) return null;
    CubeSchema parsed;
    try (InputStream in = Files.newInputStream(file)) {
      parsed = CubeSchema.fromYaml(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!parsed.id().equals(id)) {
      throw new InvalidCubeSchemaException(
          "Schema file '%s' declares id '%s' but filename implies id '%s'"
              .formatted(file, parsed.id(), id));
    }
    cache.putIfAbsent(id, parsed);
    return cache.get(id);
  }

  @Override
  public void register(CubeSchema schema) {
    throw new UnsupportedOperationException(
        "FileSystemSchemaRepository.register requires a YAML representation; use registerYaml(InputStream).");
  }

  @Override
  public CubeSchema registerYaml(InputStream input) {
    byte[] bytes;
    try {
      bytes = input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    CubeSchema parsed = CubeSchema.fromYaml(new java.io.ByteArrayInputStream(bytes));
    CubeSchema existing = cache.get(parsed.id());
    if (existing != null && !existing.equals(parsed)) {
      throw new SchemaAlreadyRegisteredException(parsed.id());
    }
    Path target = root.resolve(parsed.id() + ".yaml");
    try {
      Files.write(
          target,
          bytes,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    cache.put(parsed.id(), parsed);
    return parsed;
  }

  private Path locate(String id) {
    Path yaml = root.resolve(id + ".yaml");
    if (Files.isRegularFile(yaml)) return yaml;
    Path yml = root.resolve(id + ".yml");
    if (Files.isRegularFile(yml)) return yml;
    return null;
  }
}
