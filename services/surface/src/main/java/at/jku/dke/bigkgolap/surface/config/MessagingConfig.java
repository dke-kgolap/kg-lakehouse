package at.jku.dke.bigkgolap.surface.config;

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
          if (p instanceof KafkaProducer<?, ?> kp) {
            new KafkaClientMetrics((KafkaProducer<String, byte[]>) kp).bindTo(registry);
          }
          return tracingProducer.wrap(p);
        },
        c -> {
          if (c instanceof KafkaConsumer<?, ?> kc) {
            new KafkaClientMetrics((KafkaConsumer<String, byte[]>) kc).bindTo(registry);
          }
          return tracingConsumer.wrap(c);
        });
  }
}
