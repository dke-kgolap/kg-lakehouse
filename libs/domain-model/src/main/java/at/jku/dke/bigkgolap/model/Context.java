package at.jku.dke.bigkgolap.model;

import at.jku.dke.bigkgolap.model.hash.Sha256;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class Context {

  private final SortedMap<String, Hierarchy> hierarchies;
  private volatile String id;

  private Context(SortedMap<String, Hierarchy> hierarchies) {
    this.hierarchies = hierarchies;
  }

  public SortedMap<String, Hierarchy> hierarchies() {
    return hierarchies;
  }

  public String id() {
    if (id == null) {
      synchronized (this) {
        if (id == null) {
          id = computeId();
        }
      }
    }
    return id;
  }

  public Hierarchy getHierarchy(String dimension) {
    return hierarchies.get(dimension);
  }

  public Map<String, String> flatMembers() {
    var out = new LinkedHashMap<String, String>();
    for (var entry : hierarchies.entrySet()) {
      String dim = entry.getKey();
      for (var m : entry.getValue().members()) {
        out.put(dim + "_" + m.level().name(), m.value());
      }
    }
    return out;
  }

  /**
   * this context rolls up to {@code general} iff, for every dimension, {@code general}'s hierarchy
   * is a prefix of this context's hierarchy on that dimension. ALL-level hierarchies in {@code
   * general} (empty member list) match any specific hierarchy.
   */
  @SuppressWarnings(
      "unused") // schema is part of the API contract; reserved for future schema-aware checks
  public boolean rollsUpTo(Context general, CubeSchema schema) {
    for (var entry : hierarchies.entrySet()) {
      String dim = entry.getKey();
      Hierarchy specificH = entry.getValue();
      Hierarchy generalH = general.hierarchies.get(dim);
      if (generalH == null) return false;
      if (generalH.members().size() > specificH.members().size()) return false;
      for (int i = 0; i < generalH.members().size(); i++) {
        if (!generalH.members().get(i).equals(specificH.members().get(i))) return false;
      }
    }
    return true;
  }

  /**
   * produce a new context where each dimension's hierarchy is truncated to the merge level
   * specified for that dimension. If {@code mergeLevels} has no entry for a dimension, that
   * dimension's hierarchy is kept as-is. If the merge level is at the same depth or finer than the
   * stored hierarchy, the stored hierarchy is kept (cannot roll down).
   */
  public Context rollUpTo(MergeLevels mergeLevels, CubeSchema schema) {
    var rolled = new ArrayList<Hierarchy>(hierarchies.size());
    for (var entry : hierarchies.entrySet()) {
      String dim = entry.getKey();
      Hierarchy hierarchy = entry.getValue();
      Level mergeLevel = mergeLevels.levelFor(dim);
      if (mergeLevel == null || mergeLevel.depth() >= hierarchy.members().size()) {
        rolled.add(hierarchy);
      } else {
        rolled.add(Hierarchy.of(hierarchy.members().subList(0, mergeLevel.depth() + 1)));
      }
    }
    return of(rolled, schema);
  }

  private String computeId() {
    if (hierarchies.isEmpty()) {
      return Sha256.hex("EMPTY");
    }
    String canonical =
        hierarchies.values().stream().map(Hierarchy::id).collect(Collectors.joining(","));
    return Sha256.hex(canonical);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Context other)) return false;
    return id().equals(other.id());
  }

  @Override
  public int hashCode() {
    return id().hashCode();
  }

  @Override
  public String toString() {
    if (hierarchies.isEmpty()) return "EMPTY";
    return hierarchies.values().stream().map(Hierarchy::toString).collect(Collectors.joining("_"));
  }

  public static Context of(Collection<Hierarchy> hierarchies, CubeSchema schema) {
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
    for (String dim : schema.dimensionNames()) {
      byDim.putIfAbsent(dim, Hierarchy.all(dim));
    }
    return new Context(byDim);
  }
}
