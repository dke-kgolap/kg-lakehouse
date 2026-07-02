package at.jku.dke.bigkgolap.observability.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import org.apache.kafka.clients.producer.Producer;

public class TracingKafkaProducer {

  private final KafkaTelemetry telemetry;

  public TracingKafkaProducer(OpenTelemetry otel) {
    this.telemetry = KafkaTelemetry.create(otel);
  }

  public <K, V> Producer<K, V> wrap(Producer<K, V> producer) {
    return telemetry.wrap(producer);
  }
}
