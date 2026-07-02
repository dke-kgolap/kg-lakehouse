package at.jku.dke.bigkgolap.graph;

import at.jku.dke.bigkgolap.graph.config.LakehouseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {CassandraAutoConfiguration.class})
@EnableConfigurationProperties(LakehouseProperties.class)
public class GraphApplication {

  public static void main(String[] args) {
    SpringApplication.run(GraphApplication.class, args);
  }
}
