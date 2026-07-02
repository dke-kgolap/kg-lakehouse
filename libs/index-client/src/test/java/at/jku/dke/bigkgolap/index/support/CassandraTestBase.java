package at.jku.dke.bigkgolap.index.support;

import at.jku.dke.bigkgolap.index.CassandraIndexRepository;
import at.jku.dke.bigkgolap.index.CassandraSchemaInitializer;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration-test base. Tagged {@code integration} and excluded from {@code :index-client:test} by
 * default. Run with {@code mvn -pl libs/index-client verify} once the host has a Docker setup that
 * docker-java can probe successfully (CI and native Linux are fine; Docker Desktop on WSL2
 * currently returns a stub {@code /info} response that the auto-detector misreads as a 400).
 *
 * <p>The container is started manually in {@link #startCassandra()} rather than via
 * {@code @Container} / {@code @Testcontainers} so that an {@link Assumptions#assumeTrue} gate can
 * run <em>before</em> any Docker call. On a Docker-less host the class is aborted with {@code
 * "skipped"} status instead of failing the build.
 */
@Tag("integration")
public abstract class CassandraTestBase {

  private static final DockerImageName IMAGE = DockerImageName.parse("cassandra:4.1");

  @SuppressWarnings("resource")
  protected static CassandraContainer<?> cassandra;

  @BeforeAll
  static void startCassandra() {
    Assumptions.assumeTrue(
        isDockerAvailable(), "Docker daemon not reachable — skipping Cassandra integration tests");
    cassandra = new CassandraContainer<>(IMAGE).withReuse(true);
    cassandra.start();
  }

  @AfterAll
  static void stopCassandra() {
    if (cassandra != null && !cassandra.isShouldBeReused()) {
      cassandra.stop();
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  protected CqlSession session;
  protected CassandraIndexRepository repo;
  protected String keyspace;

  @BeforeEach
  void setUpSession() {
    String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    keyspace = "lakehouse_test_" + uuid;
    var config =
        DriverConfigLoader.programmaticBuilder()
            .withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_QUORUM")
            .withString(DefaultDriverOption.REQUEST_TIMEOUT, "30 seconds")
            .build();
    session =
        CqlSession.builder()
            .addContactPoint(cassandra.getContactPoint())
            .withLocalDatacenter(cassandra.getLocalDatacenter())
            .withConfigLoader(config)
            .build();
    new CassandraSchemaInitializer(session, keyspace).initialize();
    repo = new CassandraIndexRepository(session, keyspace);
  }

  @AfterEach
  void tearDownSession() {
    try {
      session.execute("DROP KEYSPACE IF EXISTS " + keyspace);
    } finally {
      session.close();
    }
  }
}
