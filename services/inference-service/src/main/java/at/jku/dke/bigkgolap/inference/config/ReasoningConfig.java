package at.jku.dke.bigkgolap.inference.config;

import at.jku.dke.bigkgolap.reasoning.GenericRuleInferenceEngine;
import at.jku.dke.bigkgolap.reasoning.InferenceEngine;
import at.jku.dke.bigkgolap.reasoning.JenaRdfsOwlInferenceEngine;
import at.jku.dke.bigkgolap.reasoning.TBoxRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ReasoningConfig {

  @Bean
  public TBoxRegistry tBoxRegistry() {
    return new TBoxRegistry();
  }

  /**
   * Primary {@link InferenceEngine} for the lakehouse: Jena's {@code GenericRuleReasoner} driven by
   * each engine's declared rule file (see {@code rulesets/{engine}/}). This is what {@code
   * ContextInferenceService} picks up by default via Spring autowiring.
   */
  @Bean
  @Primary
  public InferenceEngine inferenceEngine() {
    return new GenericRuleInferenceEngine();
  }

  /**
   * Fallback backend kept available as a non-primary bean for tests, debugging, and A/B comparison
   * against {@link GenericRuleInferenceEngine}. Inject explicitly with a qualifier to use it.
   */
  @Bean
  public InferenceEngine jenaRdfsOwlInferenceEngine() {
    return new JenaRdfsOwlInferenceEngine();
  }
}
