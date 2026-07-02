package at.jku.dke.bigkgolap.reasoning;

import at.jku.dke.bigkgolap.engine.Engine;
import org.apache.jena.rdf.model.Model;

/**
 * Pluggable rule backend that derives new triples from a base graph and a TBox. Decouples the
 * inference policy (which engine) from the inference orchestration (the inference-service).
 *
 * <p>Two implementations ship today:
 *
 * <ul>
 *   <li>{@link JenaRdfsOwlInferenceEngine} — Jena's built-in RDFS / OWL-Mini reasoner driven by
 *       {@link ReasonerProfile}.
 *   <li>{@link GenericRuleInferenceEngine} — Jena {@code GenericRuleReasoner} loading a per-engine
 *       rule file (e.g. {@code aixm-ruleset-domain_range.rules}). The {@code profile} parameter is
 *       ignored; the rule set is the closure definition.
 * </ul>
 *
 * <p>The seam takes plain Jena {@link Model}s plus the source {@link Engine}, so an implementation
 * can look up per-engine artifacts (rules file, TBox) via the engine's classloader.
 */
public interface InferenceEngine {

  /**
   * Returns a fresh model holding only the <em>derived</em> triples entailed by {@code base} under
   * {@code tbox} (asserted statements excluded). {@code engine} carries the source format-specific
   * configuration (rule file, supplementary ontology) the implementation may load.
   */
  Model infer(Engine engine, Model base, Model tbox, ReasonerProfile profile);
}
