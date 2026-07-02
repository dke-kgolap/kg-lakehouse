package at.jku.dke.bigkgolap.surface;

import at.jku.dke.bigkgolap.surface.config.LakehouseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {CassandraAutoConfiguration.class})
@EnableConfigurationProperties(LakehouseProperties.class)
public class SurfaceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SurfaceApplication.class, args);
  }
}
