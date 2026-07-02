package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.grpc.ContextQuery;
import at.jku.dke.bigkgolap.grpc.GraphQueryBatchRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.query.exception.GraphNotAvailableException;
import at.jku.dke.bigkgolap.query.exception.InvalidQueryException;
import at.jku.dke.bigkgolap.query.exception.QueryTimeoutException;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GraphQueryServiceClient {

  public record QueryResult(List<String> lines, boolean fromCache) {}

  /** One context's work unit for a batched fan-out call. */
  public record ContextQuerySpec(String contextId, List<String> graphUris) {}

  /** Aggregate result of a batched call: all lines, plus per-context cache tallies. */
  public record BatchResult(List<String> lines, int cacheHits, int cacheMisses) {}

  private static final int INITIAL_CAPACITY = 64;

  private final GraphQueryServiceGrpc.GraphQueryServiceBlockingStub stub;
  private final long timeoutSeconds;

  public GraphQueryServiceClient(
      GraphQueryServiceGrpc.GraphQueryServiceBlockingStub stub,
      @Value("${lakehouse.query.fanout.timeout-seconds:60}") long timeoutSeconds) {
    this.stub = stub;
    this.timeoutSeconds = timeoutSeconds;
  }

  public QueryResult queryQuads(
      String schemaId,
      String contextId,
      Collection<String> graphUris,
      GraphRepresentation representation) {
    if (graphUris.isEmpty()) {
      throw new IllegalArgumentException("graphUris must not be empty");
    }
    GraphQueryRequest request =
        GraphQueryRequest.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setSchemaId(schemaId)
            .setContextId(contextId)
            .addAllGraphUris(graphUris)
            .setRepresentation(representation.name())
            .build();

    List<String> out = new ArrayList<>(INITIAL_CAPACITY);
    boolean fromCache = false;
    try {
      var iterator = stub.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS).queryGraph(request);
      while (iterator.hasNext()) {
        var resp = iterator.next();
        switch (representation) {
          case RDF -> out.addAll(resp.getQuadsList());
          case LPG -> out.addAll(resp.getElementsList());
          case GRAPH_FRAME -> out.addAll(resp.getRowsList());
        }
        if (resp.getFromCache()) {
          fromCache = true;
        }
      }
    } catch (StatusRuntimeException e) {
      throw map(e, schemaId, contextId);
    }
    return new QueryResult(out, fromCache);
  }

  public BatchResult queryQuadsBatch(
      String schemaId, List<ContextQuerySpec> contexts, GraphRepresentation representation) {
    if (contexts.isEmpty()) {
      throw new IllegalArgumentException("contexts must not be empty");
    }
    GraphQueryBatchRequest.Builder req =
        GraphQueryBatchRequest.newBuilder()
            .setRequestId(UUID.randomUUID().toString())
            .setSchemaId(schemaId)
            .setRepresentation(representation.name());
    for (ContextQuerySpec c : contexts) {
      if (c.graphUris().isEmpty()) {
        throw new IllegalArgumentException("graphUris must not be empty for " + c.contextId());
      }
      req.addContexts(
          ContextQuery.newBuilder().setContextId(c.contextId()).addAllGraphUris(c.graphUris()));
    }

    List<String> out = new ArrayList<>(INITIAL_CAPACITY);
    // Per-context OR-accumulation of from_cache, mirroring the single-context path.
    Map<String, Boolean> fromCacheByContext = new LinkedHashMap<>();
    try {
      var iterator =
          stub.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS).queryGraphBatch(req.build());
      while (iterator.hasNext()) {
        var resp = iterator.next();
        switch (representation) {
          case RDF -> out.addAll(resp.getQuadsList());
          case LPG -> out.addAll(resp.getElementsList());
          case GRAPH_FRAME -> out.addAll(resp.getRowsList());
        }
        fromCacheByContext.merge(resp.getContextId(), resp.getFromCache(), (a, b) -> a || b);
      }
    } catch (StatusRuntimeException e) {
      throw map(e, schemaId, contexts.get(0).contextId());
    }
    int hits = 0;
    for (boolean v : fromCacheByContext.values()) {
      if (v) hits++;
    }
    return new BatchResult(out, hits, fromCacheByContext.size() - hits);
  }

  private RuntimeException map(StatusRuntimeException e, String schemaId, String contextId) {
    return switch (e.getStatus().getCode()) {
      case NOT_FOUND ->
          new GraphNotAvailableException("No data for " + schemaId + "/" + contextId, e);
      case UNIMPLEMENTED -> new InvalidQueryException("Representation not supported", e);
      case DEADLINE_EXCEEDED ->
          new QueryTimeoutException("gRPC timeout for " + schemaId + "/" + contextId, e);
      case INVALID_ARGUMENT ->
          new InvalidQueryException("Invalid request: " + e.getStatus().getDescription(), e);
      default ->
          new GraphNotAvailableException(
              "gRPC error for " + schemaId + "/" + contextId + ": " + e.getStatus(), e);
    };
  }
}
