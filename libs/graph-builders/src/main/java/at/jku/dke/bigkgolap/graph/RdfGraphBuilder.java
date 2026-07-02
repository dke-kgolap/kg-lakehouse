package at.jku.dke.bigkgolap.graph;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

/**
 * Builds an RDF dataset with the asserted/derived module split (decision 3). The mapper writes base
 * facts into the default (asserted) graph via the no-graph {@code addTriple} overloads; reasoning
 * output is added into the {@link #DERIVED_GRAPH} named graph. On the wire these become a context's
 * {@code -mod} and {@code -inf} module graphs.
 */
public class RdfGraphBuilder implements GraphBuilder {

  /**
   * Internal named graph holding inferred (derived) triples before they are tagged {@code -inf}.
   */
  public static final String DERIVED_GRAPH = "urn:x-bigkgolap:module:derived";

  private final Dataset dataset = DatasetFactory.create();
  private final Model asserted = dataset.getDefaultModel();

  @Override
  public void addTriple(
      String subject,
      String predicate,
      String obj,
      boolean isLiteral,
      String datatype,
      String lang) {
    addTo(asserted, subject, predicate, obj, isLiteral, datatype, lang);
  }

  @Override
  public void addTriple(String subject, String predicate, String obj, String graph) {
    // Honour the named graph: a graph IRI denoting a derived module routes to DERIVED_GRAPH,
    // anything else lands in the asserted default graph.
    Model target = CkrVocabulary.isDerived(graph) ? derivedModel() : asserted;
    addTo(target, subject, predicate, obj, false, null, null);
  }

  /** Adds an inferred (derived) triple — object is a URI. */
  public void addDerived(String subject, String predicate, String obj) {
    addTo(derivedModel(), subject, predicate, obj, false, null, null);
  }

  /** Adds an inferred (derived) triple with literal flags. */
  public void addDerived(
      String subject,
      String predicate,
      String obj,
      boolean isLiteral,
      String datatype,
      String lang) {
    addTo(derivedModel(), subject, predicate, obj, isLiteral, datatype, lang);
  }

  private void addTo(
      Model model,
      String subject,
      String predicate,
      String obj,
      boolean isLiteral,
      String datatype,
      String lang) {
    var s = model.createResource(subject);
    var p = model.createProperty(predicate);
    if (!isLiteral) {
      s.addProperty(p, model.createResource(obj));
    } else if (datatype != null) {
      s.addProperty(p, model.createTypedLiteral(obj, datatype));
    } else if (lang != null) {
      s.addProperty(p, obj, lang);
    } else {
      s.addLiteral(p, obj);
    }
  }

  /** The asserted (base-fact) graph. */
  public Model assertedModel() {
    return asserted;
  }

  /** The derived (inferred) graph. */
  public Model derivedModel() {
    return dataset.getNamedModel(DERIVED_GRAPH);
  }

  @Override
  public Object build() {
    return dataset;
  }

  @Override
  public byte[] serialize(String format) {
    var lang = RDFLanguages.nameToLang(format);
    if (lang == null) {
      throw new IllegalArgumentException("Unsupported RDF format '%s'".formatted(format));
    }
    var out = new ByteArrayOutputStream();
    if (RDFLanguages.isQuads(lang)) {
      RDFDataMgr.write(out, dataset, lang);
    } else {
      // Triple formats (TURTLE, N-TRIPLES, …) flatten the union of all module graphs.
      RDFDataMgr.write(out, unionModel(), lang);
    }
    return out.toByteArray();
  }

  @Override
  public long tripleCount() {
    long count = asserted.size();
    Iterator<String> names = dataset.listNames();
    while (names.hasNext()) {
      count += dataset.getNamedModel(names.next()).size();
    }
    return count;
  }

  private Model unionModel() {
    Model union = ModelFactory.createDefaultModel();
    union.add(asserted);
    Iterator<String> names = dataset.listNames();
    while (names.hasNext()) {
      union.add(dataset.getNamedModel(names.next()));
    }
    return union;
  }
}
