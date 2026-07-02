package at.jku.dke.bigkgolap.graph.config;

import at.jku.dke.bigkgolap.messaging.KafkaMessagingService;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import at.jku.dke.bigkgolap.observability.kafka.TracingKafkaConsumer;
import at.jku.dke.bigkgolap.observability.kafka.TracingKafkaProducer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class MessagingConfig {

  @Bean(destroyMethod = "close")
  public MessagingService messagingService(
      LakehouseProperties props,
      TracingKafkaProducer tracingProducer,
      TracingKafkaConsumer tracingConsumer,
      MeterRegistry registry) {
    return new KafkaMessagingService(
        props.messaging().bootstrapServers(),
        p -> {
          if (p instanceof KafkaProducer<String, byte[]> kp) {
            new KafkaClientMetrics(kp).bindTo(registry);
          }
          return tracingProducer.wrap(p);
        },
        c -> {
          if (c instanceof KafkaConsumer<String, byte[]> kc) {
            new KafkaClientMetrics(kc).bindTo(registry);
          }
          return tracingConsumer.wrap(c);
        });
  }
}
