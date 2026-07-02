package at.jku.dke.bigkgolap.inference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lakehouse")
public record LakehouseProperties(Grpc grpc, Cache cache, Schemas schemas) {

  public record Grpc(int port) {}

  public record Cache(String kind, Redis redis) {
    public record Redis(String host, int port, long ttlMinutes) {}
  }

  public record Schemas(String root) {}
}
