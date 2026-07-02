package at.jku.dke.bigkgolap.messaging.support;

import at.jku.dke.bigkgolap.messaging.KafkaMessagingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration-test base for {@link KafkaMessagingService}. Tagged {@code integration} and excluded
 * from {@code :messaging-client:test} by default. Run via {@code mvn -pl libs/messaging-client
 * verify} on a host with Docker available.
 *
 * <p>The container is started manually in {@link #startKafka()} rather than via {@code @Container}
 * / {@code @Testcontainers} so that an {@link Assumptions#assumeTrue} gate can run <em>before</em>
 * any Docker call. On a Docker-less host the class is aborted with {@code "skipped"} status instead
 * of failing the build.
 */
@Tag("integration")
public abstract class KafkaTestBase {

  private static final DockerImageName IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.6.0");

  protected static KafkaContainer kafka;

  @BeforeAll
  static void startKafka() {
    Assumptions.assumeTrue(
        isDockerAvailable(), "Docker daemon not reachable — skipping Kafka integration tests");
    kafka = new KafkaContainer(IMAGE);
    kafka.start();
  }

  @AfterAll
  static void stopKafka() {
    if (kafka != null) {
      kafka.stop();
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  protected KafkaMessagingService service;

  @BeforeEach
  void setUp() {
    service = new KafkaMessagingService(kafka.getBootstrapServers());
  }

  @AfterEach
  void tearDown() {
    try {
      service.close();
    } catch (Exception ignored) {
      // best-effort
    }
  }
}
