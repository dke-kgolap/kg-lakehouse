package at.jku.dke.bigkgolap.ingestion.config;

import at.jku.dke.bigkgolap.storage.LocalStorageService;
import at.jku.dke.bigkgolap.storage.MinioStorageService;
import at.jku.dke.bigkgolap.storage.StorageService;
import io.minio.MinioClient;
import java.nio.file.Paths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

  @Bean
  public StorageService storageService(LakehouseProperties props) {
    return switch (props.storage().kind()) {
      case "local" -> new LocalStorageService(Paths.get(props.storage().local().root()));
      case "minio" -> {
        MinioClient client =
            MinioClient.builder()
                .endpoint(props.storage().minio().endpoint())
                .credentials(
                    props.storage().minio().accessKey(), props.storage().minio().secretKey())
                .build();
        yield new MinioStorageService(client, props.storage().minio().bucket());
      }
      default ->
          throw new IllegalStateException(
              "Unknown lakehouse.storage.kind '%s' (expected 'local' or 'minio')"
                  .formatted(props.storage().kind()));
    };
  }
}
