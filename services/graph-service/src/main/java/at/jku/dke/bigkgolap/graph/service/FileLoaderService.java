package at.jku.dke.bigkgolap.graph.service;

import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.graph.GraphBuilders;
import at.jku.dke.bigkgolap.graph.GraphFormats;
import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.index.LakehouseFile;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.storage.StorageService;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Constructs the base (asserted) CKG for a context by mapping its raw files. Reasoning is a
 * separate concern handled out-of-band by the inference-service; this service stays on the optimal
 * hot path and never runs a rule engine.
 */
@Service
public class FileLoaderService {

  private static final Logger log = LoggerFactory.getLogger(FileLoaderService.class);

  private final StorageService storage;
  private final IndexRepository index;
  private final List<Engine> engines;

  public FileLoaderService(StorageService storage, IndexRepository index, List<Engine> engines) {
    this.storage = storage;
    this.index = index;
    this.engines = engines;
  }

  public GraphResult loadAndBuild(
      String schemaId, String contextId, GraphRepresentation representation) {
    List<LakehouseFile> files = index.getFilesForContext(schemaId, contextId);
    if (files.isEmpty()) {
      throw new NoFilesForContextException(schemaId, contextId);
    }

    var builder = GraphBuilders.forRepresentation(representation);
    for (LakehouseFile file : files) {
      Engine engine = null;
      for (Engine e : engines) {
        if (e.id().equalsIgnoreCase(file.engineId())) {
          engine = e;
          break;
        }
      }
      if (engine == null) {
        throw new GraphConstructionException(
            "No engine '%s' for file %s (schema=%s)"
                .formatted(file.engineId(), file.storedName(), schemaId));
      }
      try (InputStream input = storage.load(schemaId, file.storedName())) {
        engine.mapper().map(input, builder);
      } catch (GraphConstructionException e) {
        throw e;
      } catch (Exception e) {
        throw new GraphConstructionException(
            "Failed to map file %s".formatted(file.storedName()), e);
      }
    }
    log.info(
        "Built base graph for {}/{} ({}) from {} files: {} triples",
        schemaId,
        contextId,
        representation,
        files.size(),
        builder.tripleCount());
    return new GraphResult(
        representation,
        builder.build(),
        builder.serialize(formatFor(representation)),
        builder.tripleCount(),
        false,
        null);
  }

  private static String formatFor(GraphRepresentation representation) {
    return switch (representation) {
        // N-Quads (not triple-only Thrift) so the asserted/derived module split survives caching.
      case RDF -> GraphFormats.N_QUADS;
      case LPG -> GraphFormats.GRAPHSON_V3;
      case GRAPH_FRAME -> GraphFormats.GRAPHFRAME_JSON;
    };
  }
}
