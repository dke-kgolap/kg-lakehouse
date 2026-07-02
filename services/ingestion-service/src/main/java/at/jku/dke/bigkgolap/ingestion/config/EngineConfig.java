package at.jku.dke.bigkgolap.ingestion.config;

import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.engine.Engines;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

  @Bean
  public List<Engine> engines() {
    List<Engine> discovered = Engines.discover();
    if (discovered.isEmpty()) {
      throw new IllegalStateException(
          "No Engine implementations found on the classpath. "
              + "Add `runtimeOnly(project(\":engines:aixm-engine\"))` or another engine"
              + " to ingestion-service/build.gradle.kts.");
    }
    return discovered;
  }
}
