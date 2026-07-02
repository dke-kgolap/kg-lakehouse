package at.jku.dke.bigkgolap.graph.grpc;

import at.jku.dke.bigkgolap.graph.CkrVocabulary;
import at.jku.dke.bigkgolap.graph.NQuadWriter;
import at.jku.dke.bigkgolap.graph.RdfGraphBuilder;
import at.jku.dke.bigkgolap.graph.UnsupportedRepresentationException;
import at.jku.dke.bigkgolap.graph.service.GraphConstructionService;
import at.jku.dke.bigkgolap.graph.service.GraphResult;
import at.jku.dke.bigkgolap.graph.service.NoFilesForContextException;
import at.jku.dke.bigkgolap.grpc.GraphCacheRequest;
import at.jku.dke.bigkgolap.grpc.GraphCacheResponse;
import at.jku.dke.bigkgolap.grpc.GraphQueryBatchRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryResponse;
import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GraphQueryServiceImpl extends GraphQueryServiceGrpc.GraphQueryServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(GraphQueryServiceImpl.class);

  private final GraphConstructionService construction;
  private final MeterRegistry meterRegistry;
  private final int chunkTarget;

  public GraphQueryServiceImpl(
      GraphConstructionService construction,
      MeterRegistry meterRegistry,
      @Value("${lakehouse.graph.chunk.target-quads:68}") int chunkTarget) {
    this.construction = construction;
    this.meterRegistry = meterRegistry;
    this.chunkTarget = chunkTarget;
  }

  @Override
  public void queryGraph(GraphQueryRequest request, StreamObserver<GraphQueryResponse> observer) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      if (request.getGraphUrisList().isEmpty()) {
        throw new IllegalArgumentException("graph_uris must be non-empty");
      }
      GraphRepresentation representation = parseRepresentation(request.getRepresentation());
      GraphResult result =
          construction.buildGraph(request.getSchemaId(), request.getContextId(), representation);
      streamResult(result, request.getContextId(), request.getGraphUrisList(), observer);
      observer.onCompleted();
      log.info(
          "queryGraph schema={} context={} repr={} triples={} fromCache={}",
          request.getSchemaId(),
          request.getContextId(),
          representation,
          result.tripleCount(),
          result.fromCache());
    } catch (NoFilesForContextException e) {
      observer.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
    } catch (UnsupportedRepresentationException e) {
      observer.onError(Status.UNIMPLEMENTED.withDescription(e.getMessage()).asRuntimeException());
    } catch (IllegalArgumentException e) {
      observer.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (RuntimeException e) {
      // Translate every other failure into INTERNAL; we must not let an exception escape the
      // gRPC handler thread, otherwise the channel breaks for unrelated callers.
      log.error("queryGraph failed for {}/{}", request.getSchemaId(), request.getContextId(), e);
      observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    } finally {
      sample.stop(
          meterRegistry.timer(
              "lakehouse.query.graph.construction.duration",
              "schema",
              request.getSchemaId(),
              "representation",
              parseRepresentationSafe(request.getRepresentation())));
    }
  }

  @Override
  public void clearCache(GraphCacheRequest request, StreamObserver<GraphCacheResponse> observer) {
    int cleared = construction.clearCache(request.getSchemaId(), request.getContextIdsList());
    observer.onNext(GraphCacheResponse.newBuilder().setClearedCount(cleared).build());
    observer.onCompleted();
  }

  private GraphRepresentation parseRepresentation(String value) {
    String effective = value.isBlank() ? GraphRepresentation.RDF.name() : value;
    try {
      return GraphRepresentation.valueOf(effective.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown representation '%s' (expected RDF, LPG, or GRAPH_FRAME)".formatted(value));
    }
  }

  /** Safe variant used only for metrics tagging — returns raw string on parse failure. */
  private String parseRepresentationSafe(String value) {
    try {
      return parseRepresentation(value).name();
    } catch (IllegalArgumentException e) {
      return value;
    }
  }

  @Override
  public void queryGraphBatch(
      GraphQueryBatchRequest request, StreamObserver<GraphQueryResponse> observer) {
    if (request.getContextsList().isEmpty()) {
      observer.onError(
          Status.INVALID_ARGUMENT
              .withDescription("contexts must be non-empty")
              .asRuntimeException());
      return;
    }
    GraphRepresentation representation;
    try {
      representation = parseRepresentation(request.getRepresentation());
    } catch (IllegalArgumentException e) {
      observer.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
      return;
    }
    for (var ctx : request.getContextsList()) {
      Timer.Sample sample = Timer.start(meterRegistry);
      try {
        if (ctx.getGraphUrisList().isEmpty()) {
          throw new IllegalArgumentException(
              "graph_uris must be non-empty for " + ctx.getContextId());
        }
        GraphResult result =
            construction.buildGraph(request.getSchemaId(), ctx.getContextId(), representation);
        streamResult(result, ctx.getContextId(), ctx.getGraphUrisList(), observer);
      } catch (NoFilesForContextException e) {
        observer.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        return;
      } catch (IllegalArgumentException e) {
        observer.onError(
            Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        return;
      } catch (RuntimeException e) {
        log.error("queryGraphBatch failed for {}/{}", request.getSchemaId(), ctx.getContextId(), e);
        observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        return;
      } finally {
        sample.stop(
            meterRegistry.timer(
                "lakehouse.query.graph.construction.duration",
                "schema",
                request.getSchemaId(),
                "representation",
                parseRepresentationSafe(request.getRepresentation())));
      }
    }
    observer.onCompleted();
  }

  private void streamResult(
      GraphResult result,
      String contextId,
      List<String> graphUris,
      StreamObserver<GraphQueryResponse> observer) {
    ChunkEmitter emitter =
        new ChunkEmitter(
            observer, chunkTarget, result.representation(), result.fromCache(), contextId);
    switch (result.representation()) {
      case RDF -> emitNQuads(result, graphUris, emitter);
      case LPG, GRAPH_FRAME -> emitLines(result, emitter);
    }
    emitter.flush();
  }

  private void emitNQuads(GraphResult result, List<String> graphUris, ChunkEmitter emitter) {
    var render = result.render();
    if (render != null) {
      for (String uri : graphUris) {
        var modNode = NodeFactory.createURI(CkrVocabulary.modGraph(uri));
        for (String body : render.assertedBodies()) {
          emitter.emitString(at.jku.dke.bigkgolap.graph.NQuadWriter.composeQuad(body, modNode));
        }
        var infNode = NodeFactory.createURI(CkrVocabulary.infGraph(uri));
        for (String body : render.derivedBodies()) {
          emitter.emitString(at.jku.dke.bigkgolap.graph.NQuadWriter.composeQuad(body, infNode));
        }
      }
      return;
    }
    if (!(result.model() instanceof Dataset dataset)) {
      throw new IllegalStateException(
          "RDF result must carry a Jena Dataset or a render (got null/%s)"
              .formatted(result.model() != null ? result.model().getClass() : "null"));
    }
    // The asserted (default) and derived (DERIVED_GRAPH) modules are stamped into each requested
    // context graph's -mod / -inf module IRI, mirroring the reference KG-OLAP module split.
    Graph asserted = dataset.getDefaultModel().getGraph();
    Graph derived = dataset.getNamedModel(RdfGraphBuilder.DERIVED_GRAPH).getGraph();
    for (String uri : graphUris) {
      emitModule(asserted, CkrVocabulary.modGraph(uri), emitter);
      emitModule(derived, CkrVocabulary.infGraph(uri), emitter);
    }
  }

  private void emitModule(Graph graph, String moduleGraphUri, ChunkEmitter emitter) {
    Node moduleNode = NodeFactory.createURI(moduleGraphUri);
    ExtendedIterator<org.apache.jena.graph.Triple> iterator = graph.find();
    try {
      while (iterator.hasNext()) {
        emitter.emitString(NQuadWriter.write(iterator.next(), moduleNode));
      }
    } finally {
      iterator.close();
    }
  }

  /**
   * For LPG/GraphFrame, both cache-hit and cache-miss paths yield line-oriented bytes in {@code
   * result.serialized}: GraphSON v3 elements (LPG) or JSON rows (GraphFrame). We split by newline
   * and stream each non-empty line into the right proto field.
   */
  private void emitLines(GraphResult result, ChunkEmitter emitter) {
    String text = new String(result.serialized(), StandardCharsets.UTF_8);
    for (String line : text.split("\n", -1)) {
      if (!line.isEmpty()) {
        emitter.emitString(line);
      }
    }
  }

  private static final class ChunkEmitter {
    private final StreamObserver<GraphQueryResponse> observer;
    private final int chunkTarget;
    private final GraphRepresentation representation;
    private final boolean fromCache;
    private final String contextId;
    private final GraphQueryResponse.Builder builder = GraphQueryResponse.newBuilder();
    private int inChunk = 0;

    ChunkEmitter(
        StreamObserver<GraphQueryResponse> observer,
        int chunkTarget,
        GraphRepresentation representation,
        boolean fromCache,
        String contextId) {
      this.observer = observer;
      this.chunkTarget = chunkTarget;
      this.representation = representation;
      this.fromCache = fromCache;
      this.contextId = contextId;
    }

    void emitString(String line) {
      switch (representation) {
        case RDF -> builder.addQuads(line);
        case LPG -> builder.addElements(line);
        case GRAPH_FRAME -> builder.addRows(line);
      }
      inChunk++;
      if (inChunk >= chunkTarget) flushOne();
    }

    void flush() {
      observer.onNext(builder.setFromCache(fromCache).setContextId(contextId).build());
    }

    private void flushOne() {
      observer.onNext(builder.setContextId(contextId).build());
      builder.clearQuads();
      builder.clearElements();
      builder.clearRows();
      inChunk = 0;
    }
  }
}
