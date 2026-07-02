package at.jku.dke.bigkgolap.observability.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import org.apache.kafka.clients.consumer.Consumer;

public class TracingKafkaConsumer {

  private final KafkaTelemetry telemetry;

  public TracingKafkaConsumer(OpenTelemetry otel) {
    this.telemetry = KafkaTelemetry.create(otel);
  }

  public <K, V> Consumer<K, V> wrap(Consumer<K, V> consumer) {
    return telemetry.wrap(consumer);
  }
}
