package at.jku.dke.bigkgolap.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lakehouse")
public record LakehouseProperties(
    Grpc grpc,
    Graph graph,
    Cache cache,
    Schemas schemas,
    Storage storage,
    Cassandra cassandra,
    Messaging messaging) {

  public record Grpc(int port) {}

  public record Graph(Chunk chunk, L1 l1) {
    public record Chunk(int targetQuads) {}

    public record L1(boolean enabled, int maxEntries) {}
  }

  public record Cache(String kind, Redis redis, Invalidation invalidation) {
    public record Redis(String host, int port, long ttlMinutes) {}

    public record Invalidation(String groupIdSuffix) {}
  }

  public record Schemas(String root) {}

  public record Storage(String kind, Local local, Minio minio) {
    public record Local(String root) {}

    public record Minio(String endpoint, String bucket, String accessKey, String secretKey) {}
  }

  public record Cassandra(String host, int port, String localDatacenter, String keyspace) {}

  public record Messaging(String bootstrapServers) {}
}
