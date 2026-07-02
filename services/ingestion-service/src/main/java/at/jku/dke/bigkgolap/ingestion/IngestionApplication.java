package at.jku.dke.bigkgolap.ingestion;

import at.jku.dke.bigkgolap.ingestion.config.LakehouseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {CassandraAutoConfiguration.class})
@EnableConfigurationProperties(LakehouseProperties.class)
public class IngestionApplication {

  public static void main(String[] args) {
    SpringApplication.run(IngestionApplication.class, args);
  }
}
