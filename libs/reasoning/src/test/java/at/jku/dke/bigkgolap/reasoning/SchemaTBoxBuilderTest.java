package at.jku.dke.bigkgolap.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.InputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

class SchemaTBoxBuilderTest {

  private static final String BASE = "http://example.org/bigkgolap/atm/aixm#";

  private CubeSchema atmSchema() {
    try (InputStream in = getClass().getResourceAsStream("/fixtures/atm.yaml")) {
      return CubeSchema.fromYaml(in);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void generatesSubClassOfChainFromTopicHierarchy() {
    Model tbox = new SchemaTBoxBuilder().build(atmSchema(), BASE, "topic");

    var vor = ResourceFactory.createResource(BASE + "VOR");
    var navaidEquipment = ResourceFactory.createResource(BASE + "NavaidEquipment");
    var navaid = ResourceFactory.createResource(BASE + "Navaid");

    assertThat(tbox.contains(vor, RDFS.subClassOf, navaidEquipment)).isTrue();
    assertThat(tbox.contains(navaidEquipment, RDFS.subClassOf, navaid)).isTrue();
  }

  @Test
  void skipsReflexiveSelfRollups() {
    Model tbox = new SchemaTBoxBuilder().build(atmSchema(), BASE, "topic");
    // e.g. {category: AirportHeliport, family: AirportHeliport, feature: AirportHeliport}
    var ah = ResourceFactory.createResource(BASE + "AirportHeliport");
    assertThat(tbox.contains(ah, RDFS.subClassOf, ah)).isFalse();
  }

  @Test
  void emptyModelForUnknownDimension() {
    Model tbox = new SchemaTBoxBuilder().build(atmSchema(), BASE, "no_such_dimension");
    assertThat(tbox.isEmpty()).isTrue();
  }
}
