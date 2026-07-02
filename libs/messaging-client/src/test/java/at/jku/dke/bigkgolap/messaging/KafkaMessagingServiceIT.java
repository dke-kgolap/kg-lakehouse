package at.jku.dke.bigkgolap.messaging;

import at.jku.dke.bigkgolap.messaging.support.KafkaTestBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class KafkaMessagingServiceIT extends KafkaTestBase {

  // Each test embeds a unique key in the message payload and filters the consumer handler
  // on it. The Kafka container is shared across tests (static @Container in KafkaTestBase),
  // and the consumer is configured with auto.offset.reset=earliest, so a fresh group reads
  // every prior message on the topic — filtering keeps each test's assertions isolated.

  @Test
  void ingestionTaskPublishConsumeRoundTrip() {
    String storedName = "roundtrip-" + UUID.randomUUID() + ".xml";
    var received = new ConcurrentLinkedQueue<IngestionTask>();
    String groupId = "test-" + UUID.randomUUID();
    service.consumeIngestionTasks(
        groupId,
        t -> {
          if (storedName.equals(t.storedName())) received.add(t);
        });

    var task =
        new IngestionTask(
            "atm", storedName, "aixm", storedName, 0L, Instant.parse("2026-04-26T10:00:00Z"));
    service.publishIngestionTask(task);

    waitUntil(20, () -> !received.isEmpty());
    Assertions.assertThat(received).containsExactly(task);
  }

  @Test
  void cacheInvalidationEventPublishConsumeRoundTrip() {
    String marker = "ctx-" + UUID.randomUUID();
    var received = new ConcurrentLinkedQueue<CacheInvalidationEvent>();
    String groupId = "test-" + UUID.randomUUID();
    service.consumeCacheInvalidations(
        groupId,
        e -> {
          if (e.contextIds().contains(marker)) received.add(e);
        });

    var event =
        new CacheInvalidationEvent(
            "atm",
            java.util.List.of(marker),
            java.util.List.of("RDF", "LPG", "GRAPH_FRAME"),
            Instant.parse("2026-04-26T10:00:01Z"));
    service.publishCacheInvalidation(event);

    waitUntil(20, () -> !received.isEmpty());
    Assertions.assertThat(received).containsExactly(event);
  }

  @Test
  void handlerExceptionKeepsMessageUnAckedForRedelivery() {
    String storedName = "redeliver-" + UUID.randomUUID() + ".xml";
    String groupId = "test-" + UUID.randomUUID();
    var attempts = new ConcurrentLinkedQueue<IngestionTask>();
    boolean[] firstAttempted = {false};

    service.consumeIngestionTasks(
        groupId,
        task -> {
          if (!storedName.equals(task.storedName())) return; // ignore other tests' bleed
          attempts.add(task);
          if (!firstAttempted[0]) {
            firstAttempted[0] = true;
            throw new RuntimeException("boom");
          }
        });

    var task =
        new IngestionTask(
            "atm", storedName, "aixm", storedName, 0L, Instant.parse("2026-04-26T10:00:00Z"));
    service.publishIngestionTask(task);

    waitUntil(30, () -> attempts.size() >= 2);
    Assertions.assertThat(attempts).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void permanentlyFailingHandlerRoutesPoisonPillToDltAndConsumerKeepsRunning() {
    String poisonName = "poison-" + UUID.randomUUID() + ".xml";
    String aliveName = "alive-after-poison-" + UUID.randomUUID() + ".xml";
    String groupId = "test-" + UUID.randomUUID();
    var attemptsOnPoison = new AtomicInteger(0);
    var goodReceived = new ConcurrentLinkedQueue<IngestionTask>();

    // Subscribe to the DLT topic BEFORE publishing so the assertion-side consumer cannot miss
    // the record. KafkaTestBase's broker has auto.create.topics.enable=true by default, so the
    // DLT topic comes into existence on the producer's first send.
    String dltTopic = "lakehouse.ingestion.tasks" + KafkaMessagingService.DEFAULT_DLT_TOPIC_SUFFIX;
    var dltConsumer = newRawConsumer(groupId + "-dlt-watcher");
    dltConsumer.subscribe(List.of(dltTopic));
    // Prime the consumer so the broker accepts the subscription before we publish.
    dltConsumer.poll(Duration.ofMillis(500));

    service.consumeIngestionTasks(
        groupId,
        task -> {
          if (poisonName.equals(task.storedName())) {
            attemptsOnPoison.incrementAndGet();
            throw new RuntimeException("always-fail");
          }
          if (aliveName.equals(task.storedName())) {
            goodReceived.add(task);
          }
        });

    var poison =
        new IngestionTask(
            "atm", poisonName, "aixm", poisonName, 0L, Instant.parse("2026-04-26T10:00:00Z"));
    service.publishIngestionTask(poison);

    // Wait for the handler to fail exactly DEFAULT_DLT_MAX_FAILURES times.
    waitUntil(30, () -> attemptsOnPoison.get() >= KafkaMessagingService.DEFAULT_DLT_MAX_FAILURES);
    Assertions.assertThat(attemptsOnPoison.get())
        .as("handler should be retried up to dltMaxFailures times")
        .isEqualTo(KafkaMessagingService.DEFAULT_DLT_MAX_FAILURES);

    // Poll the DLT topic until the poison record arrives.
    ConsumerRecord<String, byte[]> dltRecord = pollFor(dltConsumer, 20, poisonName);
    Assertions.assertThat(dltRecord).as("poison record must land on " + dltTopic).isNotNull();
    Assertions.assertThat(headerString(dltRecord, "x-dlt-original-topic"))
        .isEqualTo("lakehouse.ingestion.tasks");
    Assertions.assertThat(headerString(dltRecord, "x-dlt-failure-count"))
        .isEqualTo(Integer.toString(KafkaMessagingService.DEFAULT_DLT_MAX_FAILURES));
    Assertions.assertThat(headerString(dltRecord, "x-dlt-exception-class"))
        .isEqualTo(RuntimeException.class.getName());
    Assertions.assertThat(headerString(dltRecord, "x-dlt-exception-message"))
        .isEqualTo("always-fail");
    dltConsumer.close();

    // Publish a normal task: if the consumer survived the poison pill it must process this.
    var good =
        new IngestionTask(
            "atm", aliveName, "aixm", aliveName, 0L, Instant.parse("2026-04-26T10:01:00Z"));
    service.publishIngestionTask(good);
    waitUntil(20, () -> !goodReceived.isEmpty());
    Assertions.assertThat(goodReceived)
        .as("consumer should keep processing after the poison pill is routed to DLT")
        .containsExactly(good);

    // The poison handler must not be re-invoked once the record is on the DLT.
    int finalAttempts = attemptsOnPoison.get();
    Assertions.assertThat(finalAttempts).isEqualTo(KafkaMessagingService.DEFAULT_DLT_MAX_FAILURES);
  }

  private static KafkaConsumer<String, byte[]> newRawConsumer(String groupId) {
    var p = new Properties();
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(p);
  }

  /** Poll {@code consumer} until a record whose JSON payload contains {@code marker} arrives. */
  private static ConsumerRecord<String, byte[]> pollFor(
      KafkaConsumer<String, byte[]> consumer, long timeoutSeconds, String marker) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
    while (System.nanoTime() < deadline) {
      var batch = consumer.poll(Duration.ofMillis(200));
      for (var r : batch) {
        if (new String(r.value(), StandardCharsets.UTF_8).contains(marker)) {
          return r;
        }
      }
    }
    return null;
  }

  private static String headerString(ConsumerRecord<String, byte[]> r, String name) {
    var h = r.headers().lastHeader(name);
    return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
  }

  private static void waitUntil(long timeoutSeconds, java.util.function.BooleanSupplier predicate) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
    while (System.nanoTime() < deadline) {
      if (predicate.getAsBoolean()) return;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    throw new AssertionError("Predicate did not become true within " + timeoutSeconds + "s");
  }
}
