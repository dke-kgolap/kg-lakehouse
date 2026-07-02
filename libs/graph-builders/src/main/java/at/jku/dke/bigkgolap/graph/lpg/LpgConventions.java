package at.jku.dke.bigkgolap.graph.lpg;

/**
 * — codified RDF→LPG mapping rules. Pure helpers shared between {@code LpgGraphBuilder}
 * (TinkerGraph) and {@code GraphFrameBuilder} (Spark DataFrames) so the per-triple decision logic
 * lives in one place.
 */
public final class LpgConventions {

  public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
  public static final String DEFAULT_VERTEX_LABEL = "Resource";

  private LpgConventions() {}

  public static boolean isRdfType(String predicate) {
    return RDF_TYPE.equals(predicate);
  }

  /**
   * Last path segment of an IRI, used as label/property key. Falls back to the full input when the
   * IRI has no separator. Examples:
   *
   * <ul>
   *   <li>{@code http://example.org/foo#Bar} → {@code Bar}
   *   <li>{@code http://example.org/foo/Bar} → {@code Bar}
   *   <li>{@code Bar} → {@code Bar}
   * </ul>
   */
  public static String extractLastSegment(String iri) {
    int hash = iri.lastIndexOf('#');
    if (hash >= 0 && hash < iri.length() - 1) {
      return iri.substring(hash + 1);
    }
    int slash = iri.lastIndexOf('/');
    if (slash >= 0 && slash < iri.length() - 1) {
      return iri.substring(slash + 1);
    }
    return iri;
  }

  /**
   * Resolve the LPG label for a {@code rdf:type} triple's object. Literals are taken as-is (the
   * AIXM mapper's deliberate quirk). IRIs are stripped to their last segment.
   */
  public static String extractLabel(String typeValue, boolean isLiteral) {
    return isLiteral ? typeValue : extractLastSegment(typeValue);
  }
}
