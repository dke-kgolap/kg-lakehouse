package at.jku.dke.bigkgolap.query;

import at.jku.dke.bigkgolap.query.config.LakehouseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {CassandraAutoConfiguration.class})
@EnableConfigurationProperties(LakehouseProperties.class)
public class QueryApplication {

  public static void main(String[] args) {
    SpringApplication.run(QueryApplication.class, args);
  }
}
