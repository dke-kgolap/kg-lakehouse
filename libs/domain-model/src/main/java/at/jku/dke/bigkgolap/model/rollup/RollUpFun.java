package at.jku.dke.bigkgolap.model.rollup;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.UnknownRollUpFunctionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class RollUpFun {

  public interface Fn extends BiFunction<Member, CubeSchema, Member> {}

  private static final ConcurrentHashMap<String, Fn> REGISTRY = new ConcurrentHashMap<>();

  static {
    register(BuiltInRollUps.DATE_TO_MONTH, BuiltInRollUps::dateToMonth);
    register(BuiltInRollUps.DATE_TO_YEAR, BuiltInRollUps::dateToYear);
    register(BuiltInRollUps.LOOKUP, BuiltInRollUps::lookup);
  }

  private RollUpFun() {}

  public static Member rollUp(Member member, CubeSchema schema) {
    String parentName = member.level().rollupTo();
    if (parentName == null) return null;
    String fnName = member.level().rollupFunction();
    if (fnName == null) {
      throw new UnknownRollUpFunctionException("<null on level %s>".formatted(member.level()));
    }
    Fn fn = REGISTRY.get(fnName);
    if (fn == null) throw new UnknownRollUpFunctionException(fnName);
    Member rolled = fn.apply(member, schema);
    if (!rolled.level().name().equals(parentName)) {
      throw new IllegalStateException(
          "Rollup function '%s' returned a member at level '%s' but level '%s' rolls up to '%s'"
              .formatted(fnName, rolled.level().name(), member.level(), parentName));
    }
    return rolled;
  }

  public static void register(String name, Fn fn) {
    REGISTRY.put(name, fn);
  }

  public static boolean isRegistered(String name) {
    return REGISTRY.containsKey(name);
  }

  /** Visible for tests. Restoring built-ins after a test that mutates the registry. */
  public static void resetToBuiltIns() {
    REGISTRY.clear();
    register(BuiltInRollUps.DATE_TO_MONTH, BuiltInRollUps::dateToMonth);
    register(BuiltInRollUps.DATE_TO_YEAR, BuiltInRollUps::dateToYear);
    register(BuiltInRollUps.LOOKUP, BuiltInRollUps::lookup);
  }
}
