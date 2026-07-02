package at.jku.dke.bigkgolap.ingestion.fakes;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import at.jku.dke.bigkgolap.messaging.testing.SyncMessagingService;
import at.jku.dke.bigkgolap.model.repository.InMemorySchemaRepository;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestProfileConfig {

  @Bean
  @Primary
  public SchemaRepository schemaRepository() {
    return new InMemorySchemaRepository();
  }

  @Bean
  @Primary
  public IndexRepository indexRepository() {
    return new InMemoryIndexRepository();
  }

  @Bean
  @Primary
  public MessagingService messagingService() {
    return new SyncMessagingService();
  }
}
