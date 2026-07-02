package at.jku.dke.bigkgolap.graph.config;

import at.jku.dke.bigkgolap.storage.LocalStorageService;
import at.jku.dke.bigkgolap.storage.MinioStorageService;
import at.jku.dke.bigkgolap.storage.StorageService;
import io.minio.MinioClient;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

  @Bean
  @ConditionalOnProperty(name = "lakehouse.storage.kind", havingValue = "minio")
  public MinioClient minioClient(LakehouseProperties props) {
    return MinioClient.builder()
        .endpoint(props.storage().minio().endpoint())
        .credentials(props.storage().minio().accessKey(), props.storage().minio().secretKey())
        .build();
  }

  @Bean
  public StorageService storageService(
      LakehouseProperties props, @Autowired(required = false) MinioClient minioClient) {
    return switch (props.storage().kind()) {
      case "local" -> new LocalStorageService(Paths.get(props.storage().local().root()));
      case "minio" -> {
        if (minioClient == null) {
          throw new IllegalStateException("MinioClient bean missing for storage kind 'minio'");
        }
        yield new MinioStorageService(minioClient, props.storage().minio().bucket());
      }
      default ->
          throw new IllegalStateException(
              "Unknown lakehouse.storage.kind '%s' (expected 'local' or 'minio')"
                  .formatted(props.storage().kind()));
    };
  }
}
