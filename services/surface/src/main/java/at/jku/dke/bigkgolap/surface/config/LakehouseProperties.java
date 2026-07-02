package at.jku.dke.bigkgolap.surface.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lakehouse")
public record LakehouseProperties(
    Auth auth,
    Schemas schemas,
    Storage storage,
    Cassandra cassandra,
    Messaging messaging,
    Services services) {

  public record Auth(String username, String password) {}

  public record Schemas(String root) {}

  public record Storage(String kind, Local local, Minio minio) {

    public record Local(String root) {}

    public record Minio(String endpoint, String bucket, String accessKey, String secretKey) {}
  }

  public record Cassandra(String host, int port, String localDatacenter, String keyspace) {}

  public record Messaging(String bootstrapServers) {}

  public record Services(Query query) {

    public record Query(String host, int httpPort, long readTimeoutSeconds) {}
  }
}
