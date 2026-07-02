package at.jku.dke.bigkgolap.engine;

public interface GraphBuilder {

  /**
   * Add a triple with optional literal flags.
   *
   * @param subject the subject URI
   * @param predicate the predicate URI
   * @param obj the object URI or literal value
   * @param isLiteral true if obj is a literal
   * @param datatype optional XSD datatype URI (may be null)
   * @param lang optional language tag (may be null)
   */
  void addTriple(
      String subject,
      String predicate,
      String obj,
      boolean isLiteral,
      String datatype,
      String lang);

  /** Convenience overload: object is a URI, no literal flags. */
  default void addTriple(String subject, String predicate, String obj) {
    addTriple(subject, predicate, obj, false, null, null);
  }

  /** Convenience overload: specify literal flag without datatype or lang. */
  default void addTriple(String subject, String predicate, String obj, boolean isLiteral) {
    addTriple(subject, predicate, obj, isLiteral, null, null);
  }

  /** Add a triple in a named graph. */
  void addTriple(String subject, String predicate, String obj, String graph);

  Object build();

  byte[] serialize(String format);

  long tripleCount();

  /**
   * representation-specific topology stats. Returns null for builders that don't track topology
   * (e.g. RDF). LPG/GraphFrame builders return concrete vertex/edge counts.
   */
  default TopologyStats topologyStats() {
    return null;
  }
}
