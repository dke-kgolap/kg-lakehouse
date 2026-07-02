package at.jku.dke.bigkgolap.model;

import at.jku.dke.bigkgolap.model.hash.Sha256;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class Hierarchy implements Comparable<Hierarchy> {

  private final String dimension;
  private final List<Member> members;
  private volatile String id;

  private Hierarchy(String dimension, List<Member> members) {
    this.dimension = dimension;
    this.members = List.copyOf(members);
  }

  public String dimension() {
    return dimension;
  }

  public List<Member> members() {
    return members;
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

  public Member leafMember() {
    return members.isEmpty() ? null : members.get(members.size() - 1);
  }

  public boolean isAllLevel() {
    return members.isEmpty();
  }

  public Hierarchy rollUp(CubeSchema schema) {
    if (isAllLevel()) {
      throw new CannotRollUpAllLevelException(dimension);
    }
    // Schema parameter reserved for future schema-aware checks.
    schema.dimensionNames();
    if (members.size() == 1) {
      return all(dimension);
    }
    return of(members.subList(0, members.size() - 1));
  }

  private String computeId() {
    var sb = new StringBuilder();
    sb.append(dimension);
    sb.append('|');
    for (int i = 0; i < members.size(); i++) {
      if (i > 0) sb.append('|');
      var m = members.get(i);
      sb.append(m.level().name());
      sb.append('=');
      sb.append(m.value());
    }
    return Sha256.hex(sb.toString());
  }

  @Override
  public int compareTo(Hierarchy other) {
    return Comparator.comparing(Hierarchy::dimension)
        .thenComparing(Hierarchy::id)
        .compare(this, other);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Hierarchy other)) return false;
    return dimension.equals(other.dimension) && members.equals(other.members);
  }

  @Override
  public int hashCode() {
    return 31 * dimension.hashCode() + members.hashCode();
  }

  @Override
  public String toString() {
    if (members.isEmpty()) {
      return dimension + ":ALL";
    }
    var sb = new StringBuilder(dimension).append(':');
    for (int i = 0; i < members.size(); i++) {
      if (i > 0) sb.append('>');
      sb.append(members.get(i).value());
    }
    return sb.toString();
  }

  public static Hierarchy all(String dimension) {
    if (dimension == null || dimension.isBlank()) {
      throw new IllegalArgumentException("Hierarchy dimension must not be blank");
    }
    return new Hierarchy(dimension, List.of());
  }

  public static Hierarchy of(List<Member> members) {
    if (members.isEmpty()) {
      throw new InvalidHierarchyException(
          "Cannot build a Hierarchy from an empty member list. Use Hierarchy.all(dimension) for the ALL hierarchy.");
    }
    String dim = members.get(0).level().dimension();
    for (int i = 0; i < members.size(); i++) {
      var m = members.get(i);
      if (!m.level().dimension().equals(dim)) {
        throw new InvalidHierarchyException(
            "Hierarchy members must all share a dimension; expected '%s' but got '%s' at index %d"
                .formatted(dim, m.level().dimension(), i));
      }
      if (m.level().depth() != i) {
        throw new InvalidHierarchyException(
            "Hierarchy must start at depth 0 with contiguous depths; expected depth %d but got %d at index %d"
                .formatted(i, m.level().depth(), i));
      }
    }
    return new Hierarchy(dim, members);
  }

  public static Hierarchy of(Member... members) {
    return of(Arrays.asList(members));
  }
}
