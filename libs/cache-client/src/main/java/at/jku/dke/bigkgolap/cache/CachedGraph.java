package at.jku.dke.bigkgolap.cache;

import java.time.Instant;
import java.util.Arrays;

/**
 * Immutable value object holding a serialised graph blob and the instant it was fetched from cache.
 *
 * <p>Overrides {@code equals}/{@code hashCode} using {@link Arrays#equals}/{@link Arrays#hashCode}
 * because Java records use referential equality for arrays by default.
 */
public record CachedGraph(byte[] data, Instant cachedAt) {

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof CachedGraph that)) return false;
    return Arrays.equals(this.data, that.data) && this.cachedAt.equals(that.cachedAt);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(data) + cachedAt.hashCode();
  }

  @Override
  public String toString() {
    return "CachedGraph[data=" + Arrays.toString(data) + ", cachedAt=" + cachedAt + "]";
  }
}
