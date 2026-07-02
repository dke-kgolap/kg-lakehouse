package at.jku.dke.bigkgolap.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.engine.Mapper;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

class GenericRuleInferenceEngineTest {

  private static final String BASE = "http://example.org/test#";
  private static final String RULES = "test-rules.rules";

  private final GenericRuleInferenceEngine engine = new GenericRuleInferenceEngine();

  /** Minimal Engine fixture; rulesResource is parameterised so we can also test the null path. */
  private record TestEngine(String rulesResource) implements Engine {
    @Override
    public String id() {
      return "test-" + System.identityHashCode(this);
    }

    @Override
    public Set<String> supportedMediaTypes() {
      return Set.of();
    }

    @Override
    public Analyzer analyzer() {
      return null;
    }

    @Override
    public Mapper mapper() {
      return null;
    }
  }

  @Test
  void subClassOfRuleDerivesSuperTypes() {
    Model tbox = ModelFactory.createDefaultModel();
    Resource vor = tbox.createResource(BASE + "VOR");
    Resource navaidEquipment = tbox.createResource(BASE + "NavaidEquipment");
    tbox.add(vor, RDFS.subClassOf, navaidEquipment);

    Model base = ModelFactory.createDefaultModel();
    Resource subject = base.createResource(BASE + "vor-1");
    base.add(subject, RDF.type, vor);

    Model derived = engine.infer(new TestEngine(RULES), base, tbox, ReasonerProfile.RDFS);

    assertThat(derived.contains(subject, RDF.type, navaidEquipment)).isTrue();
    // asserted triple is not in the derived delta
    assertThat(derived.contains(subject, RDF.type, vor)).isFalse();
  }

  @Test
  void domainAndRangeRulesInferTypes() {
    Model tbox = ModelFactory.createDefaultModel();
    Resource runway = tbox.createResource(BASE + "Runway");
    Resource airportHeliport = tbox.createResource(BASE + "AirportHeliport");
    Property assoc = tbox.createProperty(BASE + "associatedAirportHeliport");
    tbox.add(assoc, RDFS.domain, runway);
    tbox.add(assoc, RDFS.range, airportHeliport);

    Model base = ModelFactory.createDefaultModel();
    Resource rwy = base.createResource(BASE + "rwy-1");
    Resource ap = base.createResource(BASE + "LOWS");
    base.add(rwy, assoc, ap);

    Model derived = engine.infer(new TestEngine(RULES), base, tbox, ReasonerProfile.RDFS);

    assertThat(derived.contains(rwy, RDF.type, runway)).isTrue();
    assertThat(derived.contains(ap, RDF.type, airportHeliport)).isTrue();
  }

  @Test
  void propertyChainAxiomDerivesComposedProperty() {
    Model tbox = ModelFactory.createDefaultModel();
    Property usedRunway = tbox.createProperty(BASE + "usedRunway");
    Property assoc = tbox.createProperty(BASE + "associatedAirportHeliport");
    Property atAirport = tbox.createProperty(BASE + "atAirport");
    // atAirport ≡ usedRunway ∘ associatedAirportHeliport
    tbox.add(
        atAirport, OWL2.propertyChainAxiom, tbox.createList(new RDFNode[] {usedRunway, assoc}));

    Model base = ModelFactory.createDefaultModel();
    Resource dir = base.createResource(BASE + "rwy-1-33");
    Resource rwy = base.createResource(BASE + "rwy-1");
    Resource ap = base.createResource(BASE + "LOWS");
    base.add(dir, usedRunway, rwy);
    base.add(rwy, assoc, ap);

    Model derived = engine.infer(new TestEngine(RULES), base, tbox, ReasonerProfile.RDFS);

    assertThat(derived.contains(dir, atAirport, ap)).isTrue();
  }

  @Test
  void noiseFilterDropsSchemaAxiomsAndTautologies() {
    Model tbox = ModelFactory.createDefaultModel();
    Resource vor = tbox.createResource(BASE + "VOR");
    Resource navaidEquipment = tbox.createResource(BASE + "NavaidEquipment");
    tbox.add(vor, RDFS.subClassOf, navaidEquipment);

    Model base = ModelFactory.createDefaultModel();
    Resource subject = base.createResource(BASE + "vor-1");
    base.add(subject, RDF.type, vor);

    Model derived = engine.infer(new TestEngine(RULES), base, tbox, ReasonerProfile.RDFS);

    assertThat(derived.contains(subject, RDF.type, RDFS.Resource)).isFalse();
    assertThat(derived.listStatements(null, RDFS.subClassOf, (Resource) null).hasNext()).isFalse();
    assertThat(derived.listStatements().toList())
        .allMatch(
            s ->
                !s.getSubject().isURIResource()
                    || !s.getSubject().getURI().startsWith("http://www.w3.org/"));
  }

  @Test
  void throwsWhenEngineDeclaresNoRulesResource() {
    Model tbox = ModelFactory.createDefaultModel();
    Model base = ModelFactory.createDefaultModel();
    assertThatThrownBy(() -> engine.infer(new TestEngine(null), base, tbox, ReasonerProfile.RDFS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares no rulesResource");
  }
}
