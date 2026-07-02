package at.jku.dke.bigkgolap.graph;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * CKR / OLAP metadata vocabulary used to express the asserted/derived module split, mirroring the
 * reference KG-OLAP implementation. Each context graph {@code G} has an asserted module {@code
 * G-mod} (linked by {@link #HAS_ASSERTED_MODULE}) and a derived module {@code G-inf} (linked by
 * {@link #HAS_MODULE} with {@link #CLOSURE_OF} back to the context), with {@link #DERIVED_FROM}
 * provenance. Coverage between contexts uses {@link #COVERS} / {@link #ROLLS_UP_TO}.
 */
public final class CkrVocabulary {

  public static final String CKR_NS = "http://dkm.fbk.eu/ckr/meta#";
  public static final String OLAP_NS = "http://dkm.fbk.eu/ckr/olap-model#";

  public static final Property HAS_ASSERTED_MODULE =
      ResourceFactory.createProperty(CKR_NS + "hasAssertedModule");
  public static final Property HAS_MODULE = ResourceFactory.createProperty(CKR_NS + "hasModule");
  public static final Property CLOSURE_OF = ResourceFactory.createProperty(CKR_NS + "closureOf");
  public static final Property DERIVED_FROM =
      ResourceFactory.createProperty(CKR_NS + "derivedFrom");

  public static final Property COVERS = ResourceFactory.createProperty(OLAP_NS + "covers");
  public static final Property ROLLS_UP_TO = ResourceFactory.createProperty(OLAP_NS + "rollsUpTo");

  /** IRI suffix of an asserted module (base facts). */
  public static final String MOD_SUFFIX = "-mod";

  /** IRI suffix of a derived module (inferred closure). */
  public static final String INF_SUFFIX = "-inf";

  private CkrVocabulary() {}

  /** Asserted module graph IRI for a context graph IRI. */
  public static String modGraph(String contextGraphUri) {
    return contextGraphUri + MOD_SUFFIX;
  }

  /** Derived module graph IRI for a context graph IRI. */
  public static String infGraph(String contextGraphUri) {
    return contextGraphUri + INF_SUFFIX;
  }

  /** Whether a graph IRI denotes a derived (inferred) module. */
  public static boolean isDerived(String graphUri) {
    return graphUri != null && graphUri.endsWith(INF_SUFFIX);
  }
}
