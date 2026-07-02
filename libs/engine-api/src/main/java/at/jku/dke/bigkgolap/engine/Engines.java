package at.jku.dke.bigkgolap.engine;

import java.util.List;
import java.util.ServiceLoader;

/** Discovery utility for {@link Engine} implementations registered via ServiceLoader. */
public final class Engines {

  private Engines() {}

  public static List<Engine> discover(ClassLoader classLoader) {
    return ServiceLoader.load(Engine.class, classLoader).stream()
        .map(ServiceLoader.Provider::get)
        .toList();
  }

  /** Discovers engines using the current thread's context class loader. */
  public static List<Engine> discover() {
    return discover(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns the first engine whose {@link Engine#supportedMediaTypes()} contains {@code mediaType}
   * (case-insensitive), or {@code null} if none match.
   */
  public static Engine find(String mediaType, ClassLoader classLoader) {
    return discover(classLoader).stream()
        .filter(
            engine ->
                engine.supportedMediaTypes().stream().anyMatch(t -> t.equalsIgnoreCase(mediaType)))
        .findFirst()
        .orElse(null);
  }

  /** Overload using the current thread's context class loader. */
  public static Engine find(String mediaType) {
    return find(mediaType, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns the engine whose {@link Engine#id()} equals {@code id} (case-insensitive), or {@code
   * null} if none match.
   */
  public static Engine byId(String id, ClassLoader classLoader) {
    return discover(classLoader).stream()
        .filter(engine -> engine.id().equalsIgnoreCase(id))
        .findFirst()
        .orElse(null);
  }

  /** Overload using the current thread's context class loader. */
  public static Engine byId(String id) {
    return byId(id, Thread.currentThread().getContextClassLoader());
  }
}
