package at.jku.dke.bigkgolap.reasoning;

import java.util.function.Predicate;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * Materializes the RDFS/OWL closure of a cell's asserted triples against a TBox and returns the
 * <em>derived</em> triples only (the deductions, minus asserted statements and minus schema-level
 * tautologies).
 *
 * <p>This is the per-cell "local closure" of the reference KG-OLAP implementation ({@code
 * atm-3D-ruleset.ttl}): instance-level type/property generalization such as {@code x rdf:type
 * aixm:VOR} ⇒ {@code x rdf:type aixm:NavaidEquipment}, {@code aixm:Navaid}.
 */
public final class RdfsReasonerService {

  /**
   * Default filter that keeps only useful instance-level derivations. Drops: statements whose
   * object is {@code rdfs:Resource}; schema-level axioms ({@code subClassOf}/{@code subPropertyOf}/
   * {@code domain}/{@code range}); {@code rdf:type} to meta-classes ({@code rdfs:Class}, {@code
   * rdf:Property}, {@code owl:Thing}, {@code rdfs:Resource}); and reflexive statements.
   */
  public static final Predicate<Statement> DEFAULT_NOISE_FILTER =
      RdfsReasonerService::isUsefulDeduction;

  private final Predicate<Statement> noiseFilter;

  public RdfsReasonerService() {
    this(DEFAULT_NOISE_FILTER);
  }

  public RdfsReasonerService(Predicate<Statement> noiseFilter) {
    this.noiseFilter = noiseFilter;
  }

  /** Builds a reasoner with the TBox bound once, for reuse across many asserted models. */
  public Reasoner bindTBox(Model tbox, ReasonerProfile profile) {
    Reasoner base =
        profile == ReasonerProfile.OWL_MINI
            ? ReasonerRegistry.getOWLMiniReasoner()
            : ReasonerRegistry.getRDFSReasoner();
    return base.bindSchema(tbox);
  }

  /** Convenience: derive over {@code asserted} using a freshly bound RDFS reasoner. */
  public Model deductions(Model asserted, Model tbox) {
    return deductions(asserted, bindTBox(tbox, ReasonerProfile.RDFS));
  }

  /**
   * Returns a fresh model holding only the filtered deductions entailed by {@code asserted} under
   * the given (TBox-bound) reasoner. The asserted statements themselves are excluded.
   */
  public Model deductions(Model asserted, Reasoner boundReasoner) {
    var inf = ModelFactory.createInfModel(boundReasoner, asserted);

    // Iterate the materialised InfModel rather than getDeductionsModel(): the latter can omit
    // transitively-entailed statements (e.g. an indirect rdf:type via a subClassOf chain) that a
    // full listing forces the reasoner to produce. Subtract the asserted base + filter noise.
    Model result = ModelFactory.createDefaultModel();
    inf.listStatements()
        .forEachRemaining(
            stmt -> {
              if (asserted.contains(stmt)) {
                return;
              }
              if (noiseFilter.test(stmt)) {
                result.add(stmt);
              }
            });
    return result;
  }

  private static boolean isUsefulDeduction(Statement stmt) {
    Property p = stmt.getPredicate();
    RDFNode o = stmt.getObject();

    if (stmt.getSubject().equals(o)) {
      return false; // reflexive
    }
    // Drop statements about vocabulary terms themselves (e.g. rdf:nil a rdf:List, rdf:XMLLiteral a
    // rdfs:Datatype) — RDFS axiomatic tautologies, not a cell's domain knowledge.
    if (stmt.getSubject().isURIResource() && isVocabularyTerm(stmt.getSubject().getURI())) {
      return false;
    }
    if (o.equals(RDFS.Resource)) {
      return false;
    }
    if (p.equals(RDFS.subClassOf)
        || p.equals(RDFS.subPropertyOf)
        || p.equals(RDFS.domain)
        || p.equals(RDFS.range)) {
      return false; // schema-level, belongs to the global TBox, not a cell's derived module
    }
    // TBox structural plumbing surfaced by GenericRuleReasoner (built-in reasoners hide these).
    if (p.equals(OWL2.propertyChainAxiom) || p.equals(RDF.first) || p.equals(RDF.rest)) {
      return false;
    }
    if (p.equals(RDF.type)
        && (o.equals(RDFS.Class)
            || o.equals(OWL.Class)
            || o.equals(OWL2.Class)
            || o.equals(RDF.Property)
            || o.equals(RDF.List)
            || o.equals(OWL.Thing)
            || o.equals(RDFS.Resource))) {
      return false;
    }
    return true;
  }

  private static boolean isVocabularyTerm(String uri) {
    return uri.startsWith(RDF.getURI())
        || uri.startsWith(RDFS.getURI())
        || uri.startsWith(OWL.getURI())
        || uri.startsWith("http://www.w3.org/2001/XMLSchema#");
  }
}
