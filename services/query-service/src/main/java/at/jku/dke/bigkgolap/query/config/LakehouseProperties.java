package at.jku.dke.bigkgolap.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lakehouse")
public record LakehouseProperties(
    Schemas schemas, Cassandra cassandra, Graph graph, Inference inference, Query query) {

  public record Schemas(String root) {}

  public record Cassandra(String host, int port, String localDatacenter, String keyspace) {}

  public record Graph(Grpc grpc) {
    public record Grpc(String host, int port) {}
  }

  public record Inference(Grpc grpc) {
    public record Grpc(String host, int port) {}
  }

  public record Query(Fanout fanout, GraphConfig graph) {
    public record Fanout(int poolSize, long timeoutSeconds, int batchSize) {}

    public record GraphConfig(String baseUri) {}
  }
}
