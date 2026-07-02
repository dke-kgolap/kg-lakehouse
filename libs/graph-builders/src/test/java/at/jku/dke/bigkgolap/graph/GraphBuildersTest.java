package at.jku.dke.bigkgolap.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.graph.lpg.LpgGraphBuilder;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import org.junit.jupiter.api.Test;

class GraphBuildersTest {

  @Test
  void forRepresentationRdfReturnsRdfGraphBuilder() {
    var builder = GraphBuilders.forRepresentation(GraphRepresentation.RDF);
    assertThat(builder).isInstanceOf(RdfGraphBuilder.class);
  }

  @Test
  void forRepresentationLpgReturnsLpgGraphBuilder() {
    var builder = GraphBuilders.forRepresentation(GraphRepresentation.LPG);
    assertThat(builder).isInstanceOf(LpgGraphBuilder.class);
  }

  @Test
  void forRepresentationGraphFrameThrowsWhenSparkNotOnClasspath() {
    // :graph-builders' own test classpath does not include :graph-builders-spark, so the
    // ServiceLoader path returns no provider and we expect the documented error.
    assertThatThrownBy(() -> GraphBuilders.forRepresentation(GraphRepresentation.GRAPH_FRAME))
        .isInstanceOf(UnsupportedRepresentationException.class)
        .hasMessageContaining("graph-builders-spark");
  }
}
