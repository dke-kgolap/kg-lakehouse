package at.jku.dke.bigkgolap.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lakehouse")
public record LakehouseProperties(
    Schemas schemas, Storage storage, Cassandra cassandra, Messaging messaging) {

  public record Schemas(String root) {}

  public record Storage(String kind, Local local, Minio minio) {

    public record Local(String root) {}

    public record Minio(String endpoint, String bucket, String accessKey, String secretKey) {}
  }

  public record Cassandra(String host, int port, String localDatacenter, String keyspace) {}

  public record Messaging(String bootstrapServers, String consumerGroup) {}
}
