package at.jku.dke.bigkgolap.inference.cache;

import java.util.List;
import java.util.Optional;

/** No-op {@link DerivedCache}: always recomputes. */
public final class NoDerivedCache implements DerivedCache {

  public static final NoDerivedCache INSTANCE = new NoDerivedCache();

  private NoDerivedCache() {}

  @Override
  public Optional<List<String>> get(String key) {
    return Optional.empty();
  }

  @Override
  public void put(String key, List<String> derivedTriples) {
    // no-op
  }
}
