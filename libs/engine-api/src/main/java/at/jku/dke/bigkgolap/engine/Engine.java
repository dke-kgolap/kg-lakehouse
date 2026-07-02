package at.jku.dke.bigkgolap.engine;

import java.util.Set;

public interface Engine {
  String id();

  Set<String> supportedMediaTypes();

  Analyzer analyzer();

  Mapper mapper();

  /**
   * Optional hand-authored supplementary TBox (domain/range/property-chain axioms), as a classpath
   * resource name (Turtle) resolvable from this engine's classloader, or {@code null} if none. It
   * is merged with the schema-derived {@code subClassOf} axioms when reasoning. Starts empty and
   * grows toward richer inference with no architectural change.
   */
  default String ontologyResource() {
    return null;
  }

  /**
   * Optional Jena {@code GenericRuleReasoner} rule file (e.g. {@code aixm-ruleset.rules}), as a
   * classpath resource name resolvable from this engine's classloader, or {@code null} if none.
   * When set and the lakehouse is configured to use {@code GenericRuleInferenceEngine}, the rule
   * set replaces Jena's built-in RDFS/OWL closure for this engine.
   */
  default String rulesResource() {
    return null;
  }

  /**
   * Base IRI used both for the {@code rdf:type} objects this engine's mapper emits and for the
   * generated class axioms, so the two align. {@code null} disables schema-derived TBox generation.
   */
  default String typeBaseUri() {
    return null;
  }

  /** The dimension whose hierarchy yields the auto-generated {@code subClassOf} TBox. */
  default String typeHierarchyDimension() {
    return "topic";
  }
}
