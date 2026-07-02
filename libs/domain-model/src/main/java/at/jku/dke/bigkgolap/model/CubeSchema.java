package at.jku.dke.bigkgolap.model;

import at.jku.dke.bigkgolap.model.rollup.RollUpFun;
import at.jku.dke.bigkgolap.model.yaml.CubeSchemaLoader;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class CubeSchema {

  public static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]*$");

  private final String id;
  private final SortedMap<String, Dimension> dimensions;

  public CubeSchema(String id, Map<String, Dimension> dimensions) {
    if (!ID_PATTERN.matcher(id).matches()) {
      throw new InvalidCubeSchemaException(
          "Schema id '%s' must match %s".formatted(id, ID_PATTERN.pattern()));
    }
    if (dimensions.isEmpty()) {
      throw new InvalidCubeSchemaException(
          "Schema '%s' must declare at least one dimension".formatted(id));
    }
    for (var entry : dimensions.entrySet()) {
      String name = entry.getKey();
      Dimension dim = entry.getValue();
      if (!dim.name().equals(name)) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': dimension key '%s' does not match dimension name '%s'"
                .formatted(id, name, dim.name()));
      }
      for (Level level : dim.levels()) {
        String fn = level.rollupFunction();
        if (fn != null && !RollUpFun.isRegistered(fn)) {
          throw new InvalidCubeSchemaException(
              "Schema '%s': level '%s' references unknown rollup function '%s'"
                  .formatted(id, level, fn));
        }
      }
    }
    this.id = id;
    this.dimensions = new TreeMap<>(dimensions);
  }

  public String id() {
    return id;
  }

  public SortedMap<String, Dimension> dimensions() {
    return dimensions;
  }

  public Level locate(String dimensionName, String levelId) {
    Dimension dim = dimensions.get(dimensionName);
    return dim != null ? dim.level(levelId) : null;
  }

  public boolean rollsUpTo(Level from, Level to) {
    if (!from.dimension().equals(to.dimension())) return false;
    if (from.equals(to)) return true;
    Level current = from;
    while (true) {
      String parentName = current.rollupTo();
      if (parentName == null) return false;
      if (parentName.equals(to.name())) return true;
      current = locate(current.dimension(), parentName);
      if (current == null) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': level '%s' rolls up to unknown level '%s'"
                .formatted(id, from, parentName));
      }
    }
  }

  public SortedSet<String> dimensionNames() {
    return new TreeSet<>(dimensions.keySet());
  }

  public List<Level> allLevels() {
    return dimensions.values().stream().flatMap(d -> d.levels().stream()).toList();
  }

  public List<Level> levelsOf(String dimensionName) {
    Dimension dim = dimensions.get(dimensionName);
    return dim != null ? dim.levels() : List.of();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CubeSchema other)) return false;
    return id.equals(other.id) && dimensions.equals(other.dimensions);
  }

  @Override
  public int hashCode() {
    return 31 * id.hashCode() + dimensions.hashCode();
  }

  @Override
  public String toString() {
    return "CubeSchema(id=%s, dimensions=%s)".formatted(id, dimensions.keySet());
  }

  public static CubeSchema fromYaml(InputStream input) {
    return CubeSchemaLoader.load(input);
  }
}
