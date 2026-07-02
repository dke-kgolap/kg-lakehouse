package at.jku.dke.bigkgolap.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.Test;

class NQuadWriterTest {

  @Test
  void writeProducesWellFormedNQuadWithIriObject() {
    var triple =
        Triple.create(
            NodeFactory.createURI("urn:s"),
            NodeFactory.createURI("urn:p"),
            NodeFactory.createURI("urn:o"));
    var quad = NQuadWriter.write(triple, "urn:graph:atm");
    assertThat(quad).isEqualTo("<urn:s> <urn:p> <urn:o> <urn:graph:atm> .");
  }

  @Test
  void writeProducesWellFormedNQuadWithLiteralObject() {
    var triple =
        Triple.create(
            NodeFactory.createURI("urn:s"),
            NodeFactory.createURI("urn:p"),
            NodeFactory.createLiteralString("hello"));
    var quad = NQuadWriter.write(triple, "urn:graph:atm");
    assertThat(quad).contains("\"hello\"");
    assertThat(quad).endsWith("<urn:graph:atm> .");
  }

  @Test
  void writeAcceptsPreBuiltGraphNode() {
    var triple =
        Triple.create(
            NodeFactory.createURI("urn:s"),
            NodeFactory.createURI("urn:p"),
            NodeFactory.createURI("urn:o"));
    var graphNode = NodeFactory.createURI("urn:graph:weather");
    var quad = NQuadWriter.write(triple, graphNode);
    assertThat(quad).endsWith("<urn:graph:weather> .");
  }

  @Test
  void composeOfBodyEqualsLegacyWrite() {
    Triple t =
        Triple.create(
            NodeFactory.createURI("urn:s"),
            NodeFactory.createURI("urn:p"),
            NodeFactory.createLiteralString("a \"quoted\" o"));
    var g = NodeFactory.createURI("urn:g");
    assertThat(NQuadWriter.composeQuad(NQuadWriter.writeBody(t), g))
        .isEqualTo(NQuadWriter.write(t, g));
  }
}
