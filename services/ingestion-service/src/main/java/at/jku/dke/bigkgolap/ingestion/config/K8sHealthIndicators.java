package at.jku.dke.bigkgolap.ingestion.config;

import com.datastax.oss.driver.api.core.CqlSession;
import java.util.Properties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("k8s")
public class K8sHealthIndicators {

  private static final int K8S_KAFKA_TIMEOUT_MS = 5_000;

  @Bean
  public HealthIndicator cassandraHealthIndicator(CqlSession session) {
    return () -> {
      try {
        session.execute("SELECT release_version FROM system.local");
        return Health.up().build();
      } catch (Exception ex) {
        return Health.down().withException(ex).build();
      }
    };
  }

  @Bean(destroyMethod = "close")
  public AdminClient kafkaAdminClient(LakehouseProperties props) {
    Properties cfg = new Properties();
    cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, props.messaging().bootstrapServers());
    cfg.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, K8S_KAFKA_TIMEOUT_MS);
    cfg.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, K8S_KAFKA_TIMEOUT_MS);
    return AdminClient.create(cfg);
  }

  @Bean
  public HealthIndicator kafkaHealthIndicator(AdminClient adminClient) {
    return () -> {
      ListTopicsOptions opts = new ListTopicsOptions().timeoutMs(K8S_KAFKA_TIMEOUT_MS);
      try {
        var topics = adminClient.listTopics(opts).names().get();
        return Health.up().withDetail("topicCount", topics.size()).build();
      } catch (Exception ex) {
        return Health.down().withException(ex).build();
      }
    };
  }
}
