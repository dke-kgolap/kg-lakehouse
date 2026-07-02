package at.jku.dke.bigkgolap.model;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Dimension {

  private final String name;
  private final List<Level> levels;
  private final List<Map<String, String>> hierarchyData;

  public Dimension(String name, List<Level> levels, List<Map<String, String>> hierarchyData) {
    if (name == null || name.isBlank()) {
      throw new InvalidCubeSchemaException("Dimension name must not be blank");
    }
    if (levels == null || levels.isEmpty()) {
      throw new InvalidCubeSchemaException("Dimension '%s' has no levels".formatted(name));
    }
    this.name = name;
    this.levels = levels.stream().sorted(Comparator.comparingInt(Level::depth)).toList();
    this.hierarchyData = hierarchyData != null ? List.copyOf(hierarchyData) : List.of();

    for (int i = 0; i < this.levels.size(); i++) {
      Level level = this.levels.get(i);
      if (!level.dimension().equals(name)) {
        throw new InvalidCubeSchemaException(
            "Dimension '%s' contains level '%s' belonging to dimension '%s'"
                .formatted(name, level.name(), level.dimension()));
      }
      if (level.depth() != i) {
        throw new InvalidCubeSchemaException(
            "Dimension '%s': levels must have contiguous depths starting at 0; found depth %d at position %d"
                .formatted(name, level.depth(), i));
      }
    }

    Map<String, Level> byName = this.levels.stream().collect(Collectors.toMap(Level::name, l -> l));
    if (byName.size() != this.levels.size()) {
      throw new InvalidCubeSchemaException(
          "Dimension '%s' has duplicate level names".formatted(name));
    }

    for (Level level : this.levels) {
      String parent = level.rollupTo();
      if (parent == null) continue;
      Level parentLevel = byName.get(parent);
      if (parentLevel == null) {
        throw new InvalidCubeSchemaException(
            "Dimension '%s': level '%s' rolls up to unknown level '%s'"
                .formatted(name, level.name(), parent));
      }
      if (parentLevel.depth() >= level.depth()) {
        throw new InvalidCubeSchemaException(
            "Dimension '%s': level '%s' (depth %d) must roll up to a shallower level (got '%s' at depth %d)"
                .formatted(
                    name, level.name(), level.depth(), parentLevel.name(), parentLevel.depth()));
      }
    }

    Level rootLevel = this.levels.get(0);
    if (rootLevel.rollupTo() != null) {
      throw new InvalidCubeSchemaException(
          "Dimension '%s': root level '%s' must have rollup_to=null (got '%s')"
              .formatted(name, rootLevel.name(), rootLevel.rollupTo()));
    }

    validateHierarchyData(byName);
  }

  public Dimension(String name, List<Level> levels) {
    this(name, levels, List.of());
  }

  public String name() {
    return name;
  }

  public List<Level> levels() {
    return levels;
  }

  public List<Map<String, String>> hierarchyData() {
    return hierarchyData;
  }

  public Level leafLevel() {
    return levels.get(levels.size() - 1);
  }

  public Level rootLevel() {
    return levels.get(0);
  }

  public Level level(String name) {
    return levels.stream().filter(l -> l.name().equals(name)).findFirst().orElse(null);
  }

  private void validateHierarchyData(Map<String, Level> byName) {
    if (hierarchyData.isEmpty()) return;
    List<Level> lookupLevels =
        levels.stream().filter(l -> "lookup".equals(l.rollupFunction())).toList();
    for (int rowIdx = 0; rowIdx < hierarchyData.size(); rowIdx++) {
      Map<String, String> row = hierarchyData.get(rowIdx);
      for (String key : row.keySet()) {
        if (!byName.containsKey(key)) {
          throw new InvalidCubeSchemaException(
              "Dimension '%s': hierarchy row %d references unknown level '%s'"
                  .formatted(name, rowIdx, key));
        }
      }
      for (Level level : lookupLevels) {
        if (!row.containsKey(level.name())) {
          throw new InvalidCubeSchemaException(
              "Dimension '%s': hierarchy row %d is missing required level '%s'"
                  .formatted(name, rowIdx, level.name()));
        }
        String parentName = level.rollupTo();
        if (parentName != null && !row.containsKey(parentName)) {
          throw new InvalidCubeSchemaException(
              "Dimension '%s': hierarchy row %d is missing parent level '%s' of lookup-rolled level '%s'"
                  .formatted(name, rowIdx, parentName, level.name()));
        }
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Dimension other)) return false;
    return name.equals(other.name)
        && levels.equals(other.levels)
        && hierarchyData.equals(other.hierarchyData);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + levels.hashCode();
    result = 31 * result + hierarchyData.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Dimension(%s, %d levels)".formatted(name, levels.size());
  }
}
