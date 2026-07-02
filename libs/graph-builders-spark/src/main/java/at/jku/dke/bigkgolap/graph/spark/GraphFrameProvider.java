package at.jku.dke.bigkgolap.graph.spark;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.GraphBuilderProvider;
import at.jku.dke.bigkgolap.model.GraphRepresentation;

/**
 * ServiceLoader entry for {@code :graph-builders-spark}. Discovered via {@code
 * META-INF/services/at.jku.dke.bigkgolap.engine.GraphBuilderProvider} so that {@code
 * :graph-builders} can wire {@code GRAPH_FRAME} lazily without compile-time coupling.
 */
public class GraphFrameProvider implements GraphBuilderProvider {

  @Override
  public GraphRepresentation representation() {
    return GraphRepresentation.GRAPH_FRAME;
  }

  @Override
  public GraphBuilder create() {
    return new GraphFrameBuilder();
  }
}
