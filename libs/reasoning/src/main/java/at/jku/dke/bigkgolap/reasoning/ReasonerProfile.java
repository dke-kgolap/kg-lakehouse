package at.jku.dke.bigkgolap.reasoning;

/**
 * Selects which Jena reasoner the {@link RdfsReasonerService} uses.
 *
 * <p>{@link #RDFS} is sufficient for the auto-generated {@code subClassOf} TBox (decision 4 of the
 * derived-knowledge plan). {@link #OWL_MINI} is required once a supplementary ontology declares
 * axioms the RDFS reasoner cannot fire — {@code owl:propertyChainAxiom}, {@code
 * owl:someValuesFrom}, {@code owl:intersectionOf}. {@link TBoxRegistry} upgrades automatically when
 * it detects those axioms, so growing toward "Option 3" stays additive.
 */
public enum ReasonerProfile {
  RDFS,
  OWL_MINI
}
