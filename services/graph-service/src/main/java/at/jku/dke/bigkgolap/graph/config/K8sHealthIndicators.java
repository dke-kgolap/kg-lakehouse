package at.jku.dke.bigkgolap.graph.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.clients.jedis.JedisPooled;

@Configuration
@Profile("k8s")
public class K8sHealthIndicators {

  @Bean
  public HealthIndicator redisHealthIndicator(JedisPooled jedisPool) {
    return () -> {
      if (jedisPool == null) {
        return Health.up().withDetail("kind", "no-cache").build();
      }
      try {
        String pong = jedisPool.ping();
        return Health.up().withDetail("ping", pong).build();
      } catch (Exception e) {
        return Health.down().withException(e).build();
      }
    };
  }

  @Bean
  public HealthIndicator minioHealthIndicator(MinioClient minio, LakehouseProperties props) {
    return () -> {
      if (minio == null) {
        return Health.up().withDetail("kind", "no-minio").build();
      }
      try {
        BucketExistsArgs args =
            BucketExistsArgs.builder().bucket(props.storage().minio().bucket()).build();
        boolean exists = minio.bucketExists(args);
        Health.Builder builder =
            new Health.Builder()
                .withDetail("bucket", props.storage().minio().bucket())
                .withDetail("exists", exists);
        return exists ? builder.up().build() : builder.down().build();
      } catch (Exception e) {
        return Health.down().withException(e).build();
      }
    };
  }
}
