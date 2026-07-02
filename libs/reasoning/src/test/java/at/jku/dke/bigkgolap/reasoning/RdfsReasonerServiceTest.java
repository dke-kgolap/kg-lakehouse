package at.jku.dke.bigkgolap.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

class RdfsReasonerServiceTest {

  private static final String BASE = "http://example.org/bigkgolap/atm/aixm#";

  private Model tbox() {
    Model t = ModelFactory.createDefaultModel();
    Resource vor = t.createResource(BASE + "VOR");
    Resource navaidEquipment = t.createResource(BASE + "NavaidEquipment");
    Resource navaid = t.createResource(BASE + "Navaid");
    t.add(vor, RDFS.subClassOf, navaidEquipment);
    t.add(navaidEquipment, RDFS.subClassOf, navaid);
    return t;
  }

  @Test
  void derivesSuperTypesAndExcludesAssertedAndNoise() {
    Model asserted = ModelFactory.createDefaultModel();
    Resource subject = asserted.createResource(BASE + "vor-1");
    Resource vor = asserted.createResource(BASE + "VOR");
    asserted.add(subject, RDF.type, vor);

    Model derived = new RdfsReasonerService().deductions(asserted, tbox());

    Resource navaidEquipment = derived.createResource(BASE + "NavaidEquipment");
    Resource navaid = derived.createResource(BASE + "Navaid");
    assertThat(derived.contains(subject, RDF.type, navaidEquipment)).isTrue();
    assertThat(derived.contains(subject, RDF.type, navaid)).isTrue();

    // asserted triple itself is not in the derived delta
    assertThat(derived.contains(subject, RDF.type, vor)).isFalse();
    // schema-level + RDFS tautology noise is filtered out
    assertThat(derived.contains(subject, RDF.type, RDFS.Resource)).isFalse();
    assertThat(derived.listStatements(null, RDFS.subClassOf, (Resource) null).hasNext()).isFalse();
    // no statement is about a vocabulary term itself (e.g. rdf:nil a rdf:List, *  a rdfs:Datatype)
    assertThat(derived.listStatements().toList())
        .allMatch(
            s ->
                !s.getSubject().isURIResource()
                    || !s.getSubject().getURI().startsWith("http://www.w3.org/"));
  }
}
