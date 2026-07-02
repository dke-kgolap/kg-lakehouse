package at.jku.dke.bigkgolap.graph.config;

import at.jku.dke.bigkgolap.cache.GraphCache;
import at.jku.dke.bigkgolap.cache.NoCache;
import at.jku.dke.bigkgolap.cache.RedisGraphCache;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.JedisPooled;

@Configuration
@Profile("!test")
public class CacheConfig {

  private static final int POOL_MAX_TOTAL = 50;
  private static final int POOL_MAX_IDLE = 20;
  private static final int POOL_MIN_IDLE = 5;
  private static final long POOL_MAX_WAIT_MS = 3_000L;

  // destroyMethod="close" is safe with a null return: Spring DisposableBeanAdapter
  // null-checks before invoking the destroy method (Spring 5.3+/6.x behaviour).
  // The null path is only reached when cache.kind=none (non-production use).
  @Bean(destroyMethod = "close")
  public JedisPooled jedisPool(LakehouseProperties props) {
    if ("redis".equals(props.cache().kind())) {
      var poolConfig = new ConnectionPoolConfig();
      poolConfig.setMaxTotal(POOL_MAX_TOTAL);
      poolConfig.setMaxIdle(POOL_MAX_IDLE);
      poolConfig.setMinIdle(POOL_MIN_IDLE);
      poolConfig.setBlockWhenExhausted(true);
      poolConfig.setMaxWait(Duration.ofMillis(POOL_MAX_WAIT_MS));
      return new JedisPooled(
          poolConfig, props.cache().redis().host(), props.cache().redis().port());
    }
    return null;
  }

  @Bean
  public at.jku.dke.bigkgolap.graph.service.InProcessGraphCache inProcessGraphCache(
      LakehouseProperties props, io.micrometer.core.instrument.MeterRegistry registry) {
    return new at.jku.dke.bigkgolap.graph.service.InProcessGraphCache(
        props.graph().l1().enabled(), props.graph().l1().maxEntries(), registry);
  }

  @Bean
  public GraphCache graphCache(LakehouseProperties props, JedisPooled jedisPool) {
    return switch (props.cache().kind()) {
      case "redis" -> {
        if (jedisPool == null) {
          throw new IllegalStateException("JedisPooled bean missing for cache kind 'redis'");
        }
        yield new RedisGraphCache(
            jedisPool, Duration.ofMinutes(props.cache().redis().ttlMinutes()));
      }
      case "none" -> NoCache.INSTANCE;
      default ->
          throw new IllegalStateException(
              "Unknown lakehouse.cache.kind '%s' (expected 'redis' or 'none')"
                  .formatted(props.cache().kind()));
    };
  }
}
