package at.jku.dke.bigkgolap.model;

import java.util.Map;
import java.util.stream.Collectors;

public record MergeLevels(Map<String, Level> levels) {

  public MergeLevels {
    for (var entry : levels.entrySet()) {
      if (!entry.getValue().dimension().equals(entry.getKey())) {
        throw new IllegalArgumentException(
            "MergeLevels: dimension key '%s' does not match level dimension '%s'"
                .formatted(entry.getKey(), entry.getValue().dimension()));
      }
    }
    levels = Map.copyOf(levels);
  }

  public Level levelFor(String dimension) {
    return levels.get(dimension);
  }

  public static MergeLevels atLeaves(CubeSchema schema) {
    var map =
        schema.dimensions().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().leafLevel()));
    return new MergeLevels(map);
  }

  public static MergeLevels atRoots(CubeSchema schema) {
    var map =
        schema.dimensions().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().rootLevel()));
    return new MergeLevels(map);
  }
}
