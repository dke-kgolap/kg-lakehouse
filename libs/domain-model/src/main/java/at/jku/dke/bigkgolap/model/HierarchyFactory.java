package at.jku.dke.bigkgolap.model;

import at.jku.dke.bigkgolap.model.rollup.RollUpFun;
import java.util.ArrayList;

public final class HierarchyFactory {

  private HierarchyFactory() {}

  public static Hierarchy create(Member member, CubeSchema schema) {
    var chain = new ArrayList<Member>();
    Member current = member;
    while (current != null) {
      chain.add(current);
      current = RollUpFun.rollUp(current, schema);
    }
    java.util.Collections.reverse(chain);
    return Hierarchy.of(chain);
  }

  public static Hierarchy create(Level level, String value, CubeSchema schema) {
    return create(new Member(level, value), schema);
  }
}
