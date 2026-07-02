package at.jku.dke.bigkgolap.index.internal;

import at.jku.dke.bigkgolap.model.Dimension;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.Member;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HierarchyCodec {

  private HierarchyCodec() {}

  public static Map<String, String> encode(Hierarchy hierarchy) {
    var map = new LinkedHashMap<String, String>();
    for (var m : hierarchy.members()) {
      map.put(m.level().name(), m.value());
    }
    return map;
  }

  public static Hierarchy decode(Dimension dimension, Map<String, String> members) {
    if (members.isEmpty()) {
      return Hierarchy.all(dimension.name());
    }
    var ordered = new ArrayList<Member>();
    for (var level : dimension.levels()) {
      if (!members.containsKey(level.name())) {
        break;
      }
      String value = members.get(level.name());
      if (value == null) {
        throw new IllegalStateException(
            "Internal: level '%s' missing after takeWhile".formatted(level.name()));
      }
      ordered.add(new Member(level, value));
    }
    return Hierarchy.of(ordered);
  }
}
