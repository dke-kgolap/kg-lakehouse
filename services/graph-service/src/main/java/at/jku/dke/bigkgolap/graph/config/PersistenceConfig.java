package at.jku.dke.bigkgolap.graph.config;

import at.jku.dke.bigkgolap.index.CassandraIndexRepository;
import at.jku.dke.bigkgolap.index.CassandraSchemaInitializer;
import at.jku.dke.bigkgolap.index.CqlSessions;
import at.jku.dke.bigkgolap.index.IndexRepository;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class PersistenceConfig {

  @Bean(destroyMethod = "close")
  public CqlSession cqlSession(LakehouseProperties props) {
    DriverConfigLoader driverConfig =
        DriverConfigLoader.programmaticBuilder()
            .withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_QUORUM")
            .withString(DefaultDriverOption.REQUEST_TIMEOUT, "30 seconds")
            .build();
    return CqlSessions.buildWithRetry(
        CqlSession.builder()
            .addContactPoint(
                new InetSocketAddress(props.cassandra().host(), props.cassandra().port()))
            .withLocalDatacenter(props.cassandra().localDatacenter())
            .withConfigLoader(driverConfig),
        Duration.ofMinutes(5),
        Duration.ofSeconds(3));
  }

  @Bean
  public IndexRepository indexRepository(CqlSession session, LakehouseProperties props) {
    new CassandraSchemaInitializer(session, props.cassandra().keyspace()).initialize();
    return new CassandraIndexRepository(session, props.cassandra().keyspace());
  }
}
