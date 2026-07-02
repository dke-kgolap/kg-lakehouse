package at.jku.dke.bigkgolap.reasoning;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Dimension;
import at.jku.dke.bigkgolap.model.Level;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * Generates an RDFS {@code subClassOf} TBox from a cube's type-bearing dimension hierarchy
 * (decision 4, "Option 1"). In the ATM schema the {@code topic} dimension is already a class
 * taxonomy over the same local names the AIXM mapper emits as {@code rdf:type}, e.g. {@code
 * {category: Navaid, family: NavaidEquipment, feature: VOR}} yields {@code aixm:VOR ⊑
 * aixm:NavaidEquipment ⊑ aixm:Navaid}.
 *
 * <p>Class IRIs are {@code baseUri + localName}, matching the type IRIs produced once the mapper
 * emits types as resources.
 */
public final class SchemaTBoxBuilder {

  /** Builds the subClassOf axioms for {@code dimensionName}; empty model if it has no hierarchy. */
  public Model build(CubeSchema schema, String baseUri, String dimensionName) {
    Model tbox = ModelFactory.createDefaultModel();
    Dimension dim = schema.dimensions().get(dimensionName);
    if (dim == null || dim.hierarchyData().isEmpty()) {
      return tbox;
    }

    // Levels ordered root -> leaf (shallow -> deep). Each adjacent (deeper ⊑ shallower) pair in a
    // hierarchy row becomes a subClassOf axiom.
    List<Level> levels =
        dim.levels().stream().sorted(Comparator.comparingInt(Level::depth)).toList();

    for (var row : dim.hierarchyData()) {
      for (int i = 1; i < levels.size(); i++) {
        String childName = row.get(levels.get(i).name());
        String parentName = row.get(levels.get(i - 1).name());
        if (childName == null || parentName == null || childName.equals(parentName)) {
          continue; // skip missing cells and reflexive self-rollups (family == feature)
        }
        Resource child = tbox.createResource(baseUri + childName);
        Resource parent = tbox.createResource(baseUri + parentName);
        tbox.add(child, RDF.type, RDFS.Class);
        tbox.add(parent, RDF.type, RDFS.Class);
        tbox.add(child, RDFS.subClassOf, parent);
      }
    }
    return tbox;
  }
}
