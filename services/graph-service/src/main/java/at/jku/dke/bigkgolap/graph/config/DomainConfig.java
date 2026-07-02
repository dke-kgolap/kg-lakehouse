package at.jku.dke.bigkgolap.graph.config;

import at.jku.dke.bigkgolap.model.repository.FileSystemSchemaRepository;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import java.nio.file.Paths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class DomainConfig {

  @Bean
  public SchemaRepository schemaRepository(LakehouseProperties props) {
    return new FileSystemSchemaRepository(Paths.get(props.schemas().root()));
  }
}
