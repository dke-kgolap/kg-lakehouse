package at.jku.dke.bigkgolap.model;

import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class SliceDiceContext {

  private final SortedMap<String, Hierarchy> hierarchies;

  private SliceDiceContext(SortedMap<String, Hierarchy> hierarchies) {
    this.hierarchies = hierarchies;
  }

  public SortedMap<String, Hierarchy> hierarchies() {
    return hierarchies;
  }

  public Hierarchy getHierarchy(String dimension) {
    return hierarchies.get(dimension);
  }

  public boolean isEmpty() {
    return hierarchies.isEmpty();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof SliceDiceContext other)) return false;
    return hierarchies.equals(other.hierarchies);
  }

  @Override
  public int hashCode() {
    return hierarchies.hashCode();
  }

  @Override
  public String toString() {
    if (hierarchies.isEmpty()) return "SliceDice(EMPTY)";
    return "SliceDice(%s)"
        .formatted(
            hierarchies.values().stream()
                .map(Hierarchy::toString)
                .collect(Collectors.joining("_")));
  }

  public static SliceDiceContext of(Collection<Hierarchy> hierarchies, CubeSchema schema) {
    var byDim = new TreeMap<String, Hierarchy>();
    for (Hierarchy h : hierarchies) {
      if (!schema.dimensions().containsKey(h.dimension())) {
        throw new InvalidContextException(
            "Hierarchy for unknown dimension '%s' (schema='%s')"
                .formatted(h.dimension(), schema.id()));
      }
      if (byDim.put(h.dimension(), h) != null) {
        throw new InvalidContextException(
            "Duplicate hierarchy for dimension '%s'".formatted(h.dimension()));
      }
    }
    return new SliceDiceContext(byDim);
  }

  public static SliceDiceContext empty() {
    return new SliceDiceContext(new TreeMap<>());
  }
}
