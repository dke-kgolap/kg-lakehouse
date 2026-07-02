package at.jku.dke.bigkgolap.cache;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisException;

public class RedisGraphCache implements GraphCache {

  private static final Logger log = LoggerFactory.getLogger(RedisGraphCache.class);

  private final JedisPooled pool;
  private final Duration ttl;
  private final String keyPrefix;
  private final Clock clock;

  public RedisGraphCache(JedisPooled pool, Duration ttl) {
    this(pool, ttl, "lakehouse:graph", Clock.systemUTC());
  }

  public RedisGraphCache(JedisPooled pool, Duration ttl, String keyPrefix, Clock clock) {
    this.pool = pool;
    this.ttl = ttl;
    this.keyPrefix = keyPrefix;
    this.clock = clock;
  }

  private byte[] key(String schemaId, String contextId, GraphRepresentation representation) {
    return "%s:%s:%s:%s"
        .formatted(keyPrefix, schemaId, contextId, representation.name())
        .getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public CachedGraph loadGraph(
      String schemaId, String contextId, GraphRepresentation representation) {
    try {
      byte[] bytes = pool.get(key(schemaId, contextId, representation));
      return bytes != null ? new CachedGraph(bytes, clock.instant()) : null;
    } catch (JedisException e) {
      log.warn(
          "Redis GET failed for {}/{}/{}: {}", schemaId, contextId, representation, e.getMessage());
      return null;
    }
  }

  @Override
  public void upsertGraph(
      String schemaId, String contextId, GraphRepresentation representation, byte[] data) {
    try {
      pool.setex(key(schemaId, contextId, representation), ttl.getSeconds(), data);
    } catch (JedisException e) {
      log.warn(
          "Redis SETEX failed for {}/{}/{}: {}",
          schemaId,
          contextId,
          representation,
          e.getMessage());
    }
  }

  @Override
  public void deleteCachedGraphs(
      String schemaId,
      Collection<String> contextIds,
      Collection<GraphRepresentation> representations) {
    if (contextIds.isEmpty() || representations.isEmpty()) return;
    List<byte[]> keyList = new ArrayList<>(contextIds.size() * representations.size());
    for (var ctx : contextIds) {
      for (var repr : representations) {
        keyList.add(key(schemaId, ctx, repr));
      }
    }
    byte[][] keys = keyList.toArray(new byte[0][]);
    try {
      pool.del(keys);
    } catch (JedisException e) {
      log.warn("Redis DEL failed for {} keys: {}", keys.length, e.getMessage());
    }
  }

  @Override
  public void clear() {
    try {
      pool.flushDB();
    } catch (JedisException e) {
      log.warn("Redis FLUSHDB failed: {}", e.getMessage());
    }
  }
}
