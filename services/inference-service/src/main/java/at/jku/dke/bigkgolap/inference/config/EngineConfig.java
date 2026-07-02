package at.jku.dke.bigkgolap.inference.config;

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
      throw new IllegalArgumentException(
          "No Engine implementations found on the classpath. Add an engine module (e.g."
              + " aixm-engine) to inference-service as a runtime dependency.");
    }
    return discovered;
  }
}
