package at.jku.dke.bigkgolap.storage.support;

import at.jku.dke.bigkgolap.storage.MinioStorageService;
import io.minio.MinioClient;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MinIOContainer;

/**
 * Integration-test base for {@link MinioStorageService}. Tagged {@code integration} and excluded
 * from {@code :storage-client:test} by default. Run via {@code mvn -pl libs/storage-client verify}
 * on a host with Docker available.
 *
 * <p>The container is started manually in {@link #startMinio()} rather than via {@code @Container}
 * / {@code @Testcontainers} so that an {@link Assumptions#assumeTrue} gate can run <em>before</em>
 * any Docker call. On a Docker-less host the class is aborted with {@code "skipped"} status instead
 * of failing the build.
 */
@Tag("integration")
public abstract class MinioTestBase {

  private static final String IMAGE = "minio/minio:RELEASE.2024-03-30T09-41-56Z";

  protected static MinIOContainer minio;

  @BeforeAll
  static void startMinio() {
    Assumptions.assumeTrue(
        isDockerAvailable(), "Docker daemon not reachable — skipping MinIO integration tests");
    minio = new MinIOContainer(IMAGE);
    minio.start();
  }

  @AfterAll
  static void stopMinio() {
    if (minio != null) {
      minio.stop();
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  protected MinioClient client;
  protected String bucket;
  protected MinioStorageService service;

  @BeforeEach
  void setUp() {
    client =
        MinioClient.builder()
            .endpoint(minio.getS3URL())
            .credentials(minio.getUserName(), minio.getPassword())
            .build();
    bucket = "lakehouse-test-" + UUID.randomUUID().toString().substring(0, 8);
    service = new MinioStorageService(client, bucket);
  }

  @AfterEach
  void tearDown() {
    try {
      service.clearAll();
    } catch (Exception ignored) {
      // best-effort cleanup
    }
  }
}
