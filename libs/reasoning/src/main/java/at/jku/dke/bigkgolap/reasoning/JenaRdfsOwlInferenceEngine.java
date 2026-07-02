package at.jku.dke.bigkgolap.reasoning;

import at.jku.dke.bigkgolap.engine.Engine;
import org.apache.jena.rdf.model.Model;

/**
 * Legacy {@link InferenceEngine}: Apache Jena RDFS / OWL-Mini closure via {@link
 * RdfsReasonerService}. The {@code engine} parameter is intentionally unused — this backend is
 * driven entirely by {@link ReasonerProfile}. Kept as a non-primary fallback for tests and A/B
 * comparison against {@link GenericRuleInferenceEngine}.
 */
public final class JenaRdfsOwlInferenceEngine implements InferenceEngine {

  private final RdfsReasonerService reasoner;

  public JenaRdfsOwlInferenceEngine() {
    this(new RdfsReasonerService());
  }

  public JenaRdfsOwlInferenceEngine(RdfsReasonerService reasoner) {
    this.reasoner = reasoner;
  }

  @Override
  public Model infer(Engine engine, Model base, Model tbox, ReasonerProfile profile) {
    return reasoner.deductions(base, reasoner.bindTBox(tbox, profile));
  }
}
