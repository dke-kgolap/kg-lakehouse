package at.jku.dke.bigkgolap.observability;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.observability.grpc.OtelGrpcClientInterceptor;
import at.jku.dke.bigkgolap.observability.grpc.OtelGrpcServerInterceptor;
import at.jku.dke.bigkgolap.observability.kafka.TracingKafkaConsumer;
import at.jku.dke.bigkgolap.observability.kafka.TracingKafkaProducer;
import at.jku.dke.bigkgolap.observability.log.QueryLogger;
import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * cross-cutting observability beans. Loaded into every Spring service via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration(after = OpenTelemetryAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class LakehouseObservability {

  /**
   * Aligns the metric label key with OpenTelemetry's {@code service.name} Resource attribute, so
   * Tempo/Prometheus/Loki cross-references use a single key end-to-end. Spring's auto-tag {@code
   * application} is stripped via the {@link #cardinalityMeterFilter} below.
   */
  @Bean
  public MeterRegistryCustomizer<MeterRegistry> lakehouseMeterCustomizer(
      @Value("${spring.application.name:unknown}") String serviceName) {
    return registry -> registry.config().commonTags(LakehouseTags.SERVICE, serviceName);
  }

  /**
   * Cardinality discipline: strip the redundant {@code application} auto-tag (we emit {@code
   * service} instead) and the unbounded {@code uri} HTTP server tag (we have ~6 endpoints and
   * tag-by-uri risks Prometheus heap blow-up).
   */
  @Bean
  public MeterFilter cardinalityMeterFilter() {
    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        List<Tag> filtered = new ArrayList<>();
        for (Tag tag : id.getTagsAsIterable()) {
          if (!tag.getKey().equals("application") && !tag.getKey().equals("uri")) {
            filtered.add(Tag.of(tag.getKey(), tag.getValue()));
          }
        }
        return id.replaceTags(filtered);
      }
    };
  }

  @Bean
  @ConditionalOnBean(OpenTelemetry.class)
  @ConditionalOnMissingBean
  public ServerInterceptor lakehouseGrpcServerInterceptor(OpenTelemetry otel) {
    return new OtelGrpcServerInterceptor(otel);
  }

  @Bean
  @ConditionalOnBean(OpenTelemetry.class)
  @ConditionalOnMissingBean
  public ClientInterceptor lakehouseGrpcClientInterceptor(OpenTelemetry otel) {
    return new OtelGrpcClientInterceptor(otel);
  }

  @Bean
  @ConditionalOnMissingBean
  public TracingKafkaProducer tracingKafkaProducer(ObjectProvider<OpenTelemetry> otel) {
    return new TracingKafkaProducer(otel.getIfAvailable(OpenTelemetry::noop));
  }

  @Bean
  @ConditionalOnMissingBean
  public TracingKafkaConsumer tracingKafkaConsumer(ObjectProvider<OpenTelemetry> otel) {
    return new TracingKafkaConsumer(otel.getIfAvailable(OpenTelemetry::noop));
  }

  @Bean
  @ConditionalOnBean(value = {IndexRepository.class, Tracer.class})
  @ConditionalOnMissingBean
  public QueryLogger queryLogger(IndexRepository index, Tracer tracer) {
    return new QueryLogger(index, tracer);
  }
}
