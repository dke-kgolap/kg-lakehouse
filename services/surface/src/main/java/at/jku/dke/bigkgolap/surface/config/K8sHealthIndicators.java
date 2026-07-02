package at.jku.dke.bigkgolap.surface.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("k8s")
public class K8sHealthIndicators {

  @Bean
  public HealthIndicator cassandraHealthIndicator(CqlSession session) {
    return () -> {
      try {
        session.execute("SELECT release_version FROM system.local");
        return Health.up().withDetail("query", "system.local").build();
      } catch (Exception e) {
        return Health.down().withException(e).build();
      }
    };
  }
}
