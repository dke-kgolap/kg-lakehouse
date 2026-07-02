package at.jku.dke.bigkgolap.graph;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.GraphBuilderProvider;
import at.jku.dke.bigkgolap.graph.lpg.LpgGraphBuilder;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.util.ServiceLoader;

public final class GraphBuilders {

  /**
   * lazy SPI lookup for representations not hard-wired here. Today only {@code GRAPH_FRAME} uses
   * the SPI (provided by {@code :graph-builders-spark}); RDF and LPG are always present so they
   * short-circuit before the SPI is consulted.
   */
  private static GraphBuilderProvider sparkProvider = null;

  private static volatile boolean sparkProviderLoaded = false;

  private GraphBuilders() {}

  public static GraphBuilder forRepresentation(GraphRepresentation representation) {
    return switch (representation) {
      case RDF -> new RdfGraphBuilder();
      case LPG -> new LpgGraphBuilder();
      case GRAPH_FRAME -> {
        var provider = getSparkProvider();
        if (provider == null) {
          throw new UnsupportedRepresentationException(
              representation,
              "Add :graph-builders-spark to the runtime classpath to enable GRAPH_FRAME.");
        }
        yield provider.create();
      }
    };
  }

  private static synchronized GraphBuilderProvider getSparkProvider() {
    if (!sparkProviderLoaded) {
      sparkProvider =
          ServiceLoader.load(GraphBuilderProvider.class).stream()
              .map(ServiceLoader.Provider::get)
              .filter(p -> p.representation() == GraphRepresentation.GRAPH_FRAME)
              .findFirst()
              .orElse(null);
      sparkProviderLoaded = true;
    }
    return sparkProvider;
  }
}
