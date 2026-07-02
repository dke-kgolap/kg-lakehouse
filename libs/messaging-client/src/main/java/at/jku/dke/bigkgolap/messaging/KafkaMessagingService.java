package at.jku.dke.bigkgolap.messaging;

import at.jku.dke.bigkgolap.messaging.internal.JsonCodec;
import at.jku.dke.bigkgolap.messaging.internal.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The producer/consumer wrapper hooks let {@code :observability} inject OpenTelemetry's Kafka
 * instrumentation without {@code messaging-client} taking an OTel runtime dependency. Defaults are
 * identity, so non-traced callers (tests, future non-Spring uses) are unaffected.
 */
public class KafkaMessagingService implements MessagingService {

  private static final int PRODUCER_RETRIES = 3;
  static final long PRODUCER_CLOSE_S = 10L;
  static final long CONSUMER_CLOSE_S = 5L;
  static final long CONSUMER_AWAIT_S = 15L;
  static final long CONSUMER_POLL_MS = 500L;

  /** Default number of consecutive failures on the same (partition, offset) before DLT routing. */
  static final int DEFAULT_DLT_MAX_FAILURES = 3;

  /** Default suffix appended to the source topic to derive the DLT topic name. */
  static final String DEFAULT_DLT_TOPIC_SUFFIX = ".dlt";

  private final Logger log = LoggerFactory.getLogger(KafkaMessagingService.class);

  private final String bootstrapServers;
  private final UnaryOperator<org.apache.kafka.clients.producer.Producer<String, byte[]>>
      producerWrapper;
  private final UnaryOperator<org.apache.kafka.clients.consumer.Consumer<String, byte[]>>
      consumerWrapper;
  private final org.apache.kafka.clients.producer.Producer<String, byte[]> producer;
  private final int dltMaxFailures;
  private final String dltTopicSuffix;
  private final CopyOnWriteArrayList<ConsumerLoop> consumers = new CopyOnWriteArrayList<>();

  /**
   * Partition-key strategy: false = key by schemaId (default, Phase B), true = key by storedName.
   */
  private final boolean partitionByFile =
      "file".equalsIgnoreCase(System.getenv("LAKEHOUSE_INGEST_PARTITION_KEY"));

  public KafkaMessagingService(String bootstrapServers) {
    this(bootstrapServers, p -> p, c -> c);
  }

  public KafkaMessagingService(
      String bootstrapServers,
      UnaryOperator<org.apache.kafka.clients.producer.Producer<String, byte[]>> producerWrapper,
      UnaryOperator<org.apache.kafka.clients.consumer.Consumer<String, byte[]>> consumerWrapper) {
    this(
        bootstrapServers,
        producerWrapper,
        consumerWrapper,
        DEFAULT_DLT_MAX_FAILURES,
        DEFAULT_DLT_TOPIC_SUFFIX);
  }

  public KafkaMessagingService(
      String bootstrapServers,
      UnaryOperator<org.apache.kafka.clients.producer.Producer<String, byte[]>> producerWrapper,
      UnaryOperator<org.apache.kafka.clients.consumer.Consumer<String, byte[]>> consumerWrapper,
      int dltMaxFailures,
      String dltTopicSuffix) {
    if (dltMaxFailures < 1) {
      throw new IllegalArgumentException("dltMaxFailures must be >= 1");
    }
    if (dltTopicSuffix == null || dltTopicSuffix.isEmpty()) {
      throw new IllegalArgumentException("dltTopicSuffix must be non-empty");
    }
    this.bootstrapServers = bootstrapServers;
    this.producerWrapper = producerWrapper;
    this.consumerWrapper = consumerWrapper;
    this.producer = producerWrapper.apply(new KafkaProducer<>(producerProps()));
    this.dltMaxFailures = dltMaxFailures;
    this.dltTopicSuffix = dltTopicSuffix;
  }

  @Override
  public void publishIngestionTask(IngestionTask task) {
    publish(Topics.INGESTION_TASKS, ingestionKey(task, partitionByFile), JsonCodec.encode(task));
  }

  /** Pure, testable key selection (no env / no Kafka). */
  static String ingestionKey(IngestionTask task, boolean partitionByFile) {
    return partitionByFile ? task.storedName() : task.schemaId();
  }

  @Override
  public void publishIngestionCompleted(IngestionCompletedEvent event) {
    publish(Topics.INGESTION_COMPLETED, event.schemaId(), JsonCodec.encode(event));
  }

  @Override
  public void publishCacheInvalidation(CacheInvalidationEvent event) {
    publish(Topics.CACHE_INVALIDATION, event.schemaId(), JsonCodec.encode(event));
  }

  @Override
  public void consumeIngestionTasks(String groupId, Consumer<IngestionTask> handler) {
    startConsumer(
        Topics.INGESTION_TASKS,
        groupId,
        bytes -> handler.accept(JsonCodec.decode(bytes, IngestionTask.class)));
  }

  @Override
  public void consumeCacheInvalidations(String groupId, Consumer<CacheInvalidationEvent> handler) {
    startConsumer(
        Topics.CACHE_INVALIDATION,
        groupId,
        bytes -> handler.accept(JsonCodec.decode(bytes, CacheInvalidationEvent.class)));
  }

  @Override
  public void close() {
    for (var loop : consumers) {
      loop.stop();
    }
    for (var loop : consumers) {
      loop.awaitTermination();
    }
    consumers.clear();
    try {
      producer.close(Duration.ofSeconds(PRODUCER_CLOSE_S));
    } catch (Exception e) {
      log.warn("Producer close raised: {}", e.getMessage());
    }
  }

  private void publish(String topic, String key, byte[] payload) {
    try {
      producer.send(new ProducerRecord<>(topic, key, payload)).get();
    } catch (Exception e) {
      throw new MessagingPublishException("Failed to publish to '" + topic + "'", e);
    }
  }

  private void startConsumer(String topic, String groupId, Consumer<byte[]> decode) {
    var loop =
        new ConsumerLoop(
            bootstrapServers,
            topic,
            groupId,
            decode,
            consumerWrapper,
            producer,
            dltMaxFailures,
            dltTopicSuffix,
            log);
    consumers.add(loop);
    loop.start();
  }

  private Properties producerProps() {
    var p = new Properties();
    p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    p.put(ProducerConfig.ACKS_CONFIG, "all");
    p.put(ProducerConfig.RETRIES_CONFIG, PRODUCER_RETRIES);
    p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    p.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
    // Batching: effective under concurrent multi-threaded ingestion (e.g., concurrency-32
    // benchmark) where multiple threads call send() simultaneously — the producer batches
    // all concurrent sends during the linger window before flushing. The per-call .get()
    // in publish() serializes within a single thread but does not prevent cross-thread batching.
    p.put(ProducerConfig.LINGER_MS_CONFIG, 10);
    p.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);
    p.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);
    return p;
  }

  // -------------------------------------------------------------------------
  // Inner class: ConsumerLoop
  // -------------------------------------------------------------------------

  static class ConsumerLoop {

    private final String topic;
    private final Consumer<byte[]> handle;
    private final Logger log;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch terminated = new CountDownLatch(1);
    private final org.apache.kafka.clients.consumer.Consumer<String, byte[]> consumer;
    private final org.apache.kafka.clients.producer.Producer<String, byte[]> producer;
    private final int dltMaxFailures;
    private final String dltTopicSuffix;
    private final Thread thread;

    /**
     * Per-partition failure tracker. Key: TopicPartition. Value: {failingOffset, failureCount}. The
     * map is only accessed from the consumer thread, so it does not need synchronisation.
     */
    private final Map<TopicPartition, long[]> failureState = new HashMap<>();

    ConsumerLoop(
        String bootstrapServers,
        String topic,
        String groupId,
        Consumer<byte[]> handle,
        UnaryOperator<org.apache.kafka.clients.consumer.Consumer<String, byte[]>> consumerWrapper,
        org.apache.kafka.clients.producer.Producer<String, byte[]> producer,
        int dltMaxFailures,
        String dltTopicSuffix,
        Logger log) {
      this.topic = topic;
      this.handle = handle;
      this.producer = producer;
      this.dltMaxFailures = dltMaxFailures;
      this.dltTopicSuffix = dltTopicSuffix;
      this.log = log;
      this.consumer =
          consumerWrapper.apply(new KafkaConsumer<>(consumerProps(bootstrapServers, groupId)));
      this.thread = new Thread(this::run, "kafka-consumer-" + topic + "-" + groupId);
      this.thread.setDaemon(true);
    }

    void start() {
      running.set(true);
      consumer.subscribe(List.of(topic));
      thread.start();
    }

    void stop() {
      running.set(false);
      try {
        consumer.wakeup();
      } catch (Exception e) {
        log.debug("wakeup raised: {}", e.getMessage());
      }
    }

    void awaitTermination() {
      try {
        terminated.await(CONSUMER_AWAIT_S, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    private void run() {
      try {
        while (running.get()) {
          ConsumerRecords<String, byte[]> records;
          try {
            records = consumer.poll(Duration.ofMillis(CONSUMER_POLL_MS));
          } catch (WakeupException e) {
            if (!running.get()) {
              break;
            } else {
              throw e;
            }
          }
          for (var record : records) {
            try {
              handle.accept(record.value());
              consumer.commitSync();
              clearFailureIfMatches(record);
            } catch (MessagingDecodeException e) {
              log.warn(
                  "Decode failure on topic={} partition={} offset={}: {}; skipping (commit-and-skip policy)",
                  record.topic(),
                  record.partition(),
                  record.offset(),
                  e.getMessage());
              consumer.commitSync();
              clearFailureIfMatches(record);
            } catch (Exception e) {
              int failures = recordFailure(record);
              if (failures >= dltMaxFailures && publishToDlt(record, e)) {
                log.warn(
                    "Poison-pill on topic={} partition={} offset={} after {} consecutive failures: {}; routed to DLT topic={} and offset committed",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    failures,
                    e.getMessage(),
                    record.topic() + dltTopicSuffix);
                consumer.commitSync(
                    Map.of(
                        new TopicPartition(record.topic(), record.partition()),
                        new OffsetAndMetadata(record.offset() + 1)));
                clearFailureIfMatches(record);
                // continue processing the rest of the batch; the next records have not yet
                // been handled and their own commitSync()s will advance the position normally.
              } else {
                log.error(
                    "Handler raised on topic={} partition={} offset={} (attempt {}/{}): {}; offset NOT committed, seeking back for redelivery",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    failures,
                    dltMaxFailures,
                    e.getMessage());
                // Reset the consumer's position so this record (and the rest of the batch we
                // hadn't reached yet) is fetched again on the next poll. Without this, poll()
                // has already advanced the in-memory position past the failed record, and
                // skipping commitSync alone wouldn't redeliver it until a rebalance/restart.
                consumer.seek(
                    new TopicPartition(record.topic(), record.partition()), record.offset());
                break;
              }
            }
          }
        }
      } finally {
        try {
          consumer.close(Duration.ofSeconds(CONSUMER_CLOSE_S));
        } catch (Exception e) {
          log.debug("consumer close raised: {}", e.getMessage());
        }
        terminated.countDown();
      }
    }

    /**
     * Increment the failure count for the (partition, offset) pair of {@code record}. If the
     * tracker holds a different offset, it is reset (the previous failing record has either
     * succeeded, been DLT'd, or been seek'd past).
     */
    private int recordFailure(ConsumerRecord<String, byte[]> record) {
      var tp = new TopicPartition(record.topic(), record.partition());
      long[] state = failureState.get(tp);
      if (state == null || state[0] != record.offset()) {
        state = new long[] {record.offset(), 0L};
        failureState.put(tp, state);
      }
      state[1]++;
      return (int) state[1];
    }

    /**
     * Remove the tracker entry for the (partition, offset) pair of {@code record} if it matches.
     * Called from the success path and immediately after a successful DLT publish.
     */
    private void clearFailureIfMatches(ConsumerRecord<String, byte[]> record) {
      var tp = new TopicPartition(record.topic(), record.partition());
      long[] state = failureState.get(tp);
      if (state != null && state[0] == record.offset()) {
        failureState.remove(tp);
      }
    }

    /**
     * Publish the poisoned {@code record} to {@code record.topic() + dltTopicSuffix}, carrying
     * headers that describe the original location and the failure cause. Returns {@code true} on
     * success and {@code false} on failure; on failure the consumer falls back to seek-back so the
     * offset is not lost.
     */
    private boolean publishToDlt(ConsumerRecord<String, byte[]> record, Exception cause) {
      String dltTopic = record.topic() + dltTopicSuffix;
      var dlt = new ProducerRecord<>(dltTopic, record.key(), record.value());
      dlt.headers().add("x-dlt-original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
      dlt.headers()
          .add(
              "x-dlt-original-partition",
              Integer.toString(record.partition()).getBytes(StandardCharsets.UTF_8));
      dlt.headers()
          .add(
              "x-dlt-original-offset",
              Long.toString(record.offset()).getBytes(StandardCharsets.UTF_8));
      dlt.headers()
          .add(
              "x-dlt-failure-count",
              Integer.toString(dltMaxFailures).getBytes(StandardCharsets.UTF_8));
      dlt.headers()
          .add(
              "x-dlt-exception-class", cause.getClass().getName().getBytes(StandardCharsets.UTF_8));
      String message = cause.getMessage();
      if (message != null) {
        dlt.headers().add("x-dlt-exception-message", message.getBytes(StandardCharsets.UTF_8));
      }
      try {
        producer.send(dlt).get();
        return true;
      } catch (Exception sendError) {
        log.error(
            "DLT publish to topic={} failed: {}; falling back to seek-back so the offset is preserved",
            dltTopic,
            sendError.getMessage());
        return false;
      }
    }

    private static Properties consumerProps(String bootstrapServers, String groupId) {
      var p = new Properties();
      p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
      p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
      p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
      return p;
    }
  }
}
