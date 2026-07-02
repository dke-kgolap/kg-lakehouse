package at.jku.dke.bigkgolap.model;

import java.util.Comparator;

public record Level(
    String name, String dimension, int depth, String rollupTo, String rollupFunction)
    implements Comparable<Level> {

  public Level {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Level name must not be blank");
    if (dimension == null || dimension.isBlank())
      throw new IllegalArgumentException("Level dimension must not be blank");
    if (depth < 0)
      throw new IllegalArgumentException(
          "Level depth must be non-negative (was %d)".formatted(depth));
    if ((rollupTo == null) != (rollupFunction == null)) {
      throw new IllegalArgumentException(
          "Level '%s.%s': rollupTo and rollupFunction must be both null or both set"
              .formatted(dimension, name));
    }
  }

  @Override
  public int compareTo(Level other) {
    return Comparator.comparing(Level::dimension)
        .thenComparingInt(Level::depth)
        .thenComparing(Level::name)
        .compare(this, other);
  }

  @Override
  public String toString() {
    return dimension + "_" + name;
  }
}
