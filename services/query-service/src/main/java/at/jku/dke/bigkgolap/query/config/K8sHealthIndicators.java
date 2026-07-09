package at.jku.dke.bigkgolap.query.config;

import com.datastax.oss.driver.api.core.CqlSession;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Qualifier;
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
        return Health.up().build();
      } catch (Exception e) {
        return Health.down().withException(e).build();
      }
    };
  }

  @Bean
  public HealthIndicator graphServiceGrpcHealthIndicator(
      @Qualifier("graphServiceChannel") ManagedChannel channel) {
    return () -> {
      // requestConnection=true nudges an idle channel toward READY without waiting on a real RPC.
      ConnectivityState state = channel.getState(true);
      Health.Builder builder = Health.status(state.name()).withDetail("state", state.name());
      return switch (state) {
        case READY, IDLE -> builder.up().build();
        default -> builder.down().build();
      };
    };
  }

  @Bean
  public HealthIndicator inferenceServiceGrpcHealthIndicator(
      @Qualifier("inferenceServiceChannel") ManagedChannel channel) {
    return () -> {
      // reasoning=true queries also fan out to inference-service, so readiness must cover this
      // channel, not just graph. Same semantics as the graph indicator (nudge; treat IDLE as up).
      ConnectivityState state = channel.getState(true);
      Health.Builder builder = Health.status(state.name()).withDetail("state", state.name());
      return switch (state) {
        case READY, IDLE -> builder.up().build();
        default -> builder.down().build();
      };
    };
  }
}
