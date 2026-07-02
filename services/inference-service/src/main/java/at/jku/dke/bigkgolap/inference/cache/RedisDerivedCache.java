package at.jku.dke.bigkgolap.inference.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

/** Redis-backed {@link DerivedCache}. Stores the derived triples as newline-joined N-Triples. */
public final class RedisDerivedCache implements DerivedCache {

  private static final String PREFIX = "inf:";

  private final JedisPooled jedis;
  private final long ttlSeconds;

  public RedisDerivedCache(JedisPooled jedis, Duration ttl) {
    this.jedis = jedis;
    this.ttlSeconds = ttl.toSeconds();
  }

  @Override
  public Optional<List<String>> get(String key) {
    String value = jedis.get(PREFIX + key);
    if (value == null) {
      return Optional.empty();
    }
    if (value.isEmpty()) {
      return Optional.of(List.of());
    }
    return Optional.of(List.of(value.split("\n")));
  }

  @Override
  public void put(String key, List<String> derivedTriples) {
    String value = String.join("\n", derivedTriples);
    jedis.set(PREFIX + key, value, new SetParams().ex(ttlSeconds));
  }
}
