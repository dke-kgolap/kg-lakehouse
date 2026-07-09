package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.grpc.InferBatchRequest;
import at.jku.dke.bigkgolap.grpc.InferContext;
import at.jku.dke.bigkgolap.grpc.InferRequest;
import at.jku.dke.bigkgolap.grpc.InferenceServiceGrpc;
import at.jku.dke.bigkgolap.query.exception.GraphNotAvailableException;
import at.jku.dke.bigkgolap.query.exception.InvalidQueryException;
import at.jku.dke.bigkgolap.query.exception.QueryTimeoutException;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Client for the inference-service: ships a context's base graph, receives derived triples. */
@Component
public class InferenceServiceClient {

  public record InferResult(List<String> derivedTriples, boolean fromCache) {}

  private final InferenceServiceGrpc.InferenceServiceBlockingStub stub;
  private final long timeoutSeconds;

  public InferenceServiceClient(
      InferenceServiceGrpc.InferenceServiceBlockingStub stub,
      @Value("${lakehouse.query.fanout.timeout-seconds:60}") long timeoutSeconds) {
    this.stub = stub;
    this.timeoutSeconds = timeoutSeconds;
  }

  public InferResult infer(
      String schemaId, String contextId, String engineId, Collection<String> baseQuads) {
    InferRequest request =
        InferRequest.newBuilder()
            .setSchemaId(schemaId)
            .setContextId(contextId)
            .setEngineId(engineId == null ? "" : engineId)
            .addAllBaseQuads(baseQuads)
            .build();

    List<String> out = new ArrayList<>();
    boolean fromCache = false;
    try {
      var iterator = stub.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS).infer(request);
      while (iterator.hasNext()) {
        var resp = iterator.next();
        out.addAll(resp.getDerivedTriplesList());
        if (resp.getFromCache()) {
          fromCache = true;
        }
      }
    } catch (StatusRuntimeException e) {
      throw map(e, schemaId, contextId);
    }
    return new InferResult(out, fromCache);
  }

  /** One context's inference work unit for a batched call. */
  public record InferContextSpec(String contextId, String engineId, List<String> baseQuads) {}

  /**
   * Batched inference: returns derived triples grouped by context id (one entry per requested
   * context).
   */
  public Map<String, List<String>> inferBatch(String schemaId, List<InferContextSpec> contexts) {
    if (contexts.isEmpty()) {
      throw new IllegalArgumentException("contexts must not be empty");
    }
    InferBatchRequest.Builder req = InferBatchRequest.newBuilder().setSchemaId(schemaId);
    Map<String, List<String>> byContext = new LinkedHashMap<>();
    for (InferContextSpec c : contexts) {
      req.addContexts(
          InferContext.newBuilder()
              .setContextId(c.contextId())
              .setEngineId(c.engineId() == null ? "" : c.engineId())
              .addAllBaseQuads(c.baseQuads()));
      byContext.put(c.contextId(), new ArrayList<>());
    }
    try {
      var iterator =
          stub.withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS).inferBatch(req.build());
      while (iterator.hasNext()) {
        var resp = iterator.next();
        byContext
            .computeIfAbsent(resp.getContextId(), k -> new ArrayList<>())
            .addAll(resp.getDerivedTriplesList());
      }
    } catch (StatusRuntimeException e) {
      throw map(e, schemaId, contexts.get(0).contextId());
    }
    return byContext;
  }

  private RuntimeException map(StatusRuntimeException e, String schemaId, String contextId) {
    return switch (e.getStatus().getCode()) {
      case INVALID_ARGUMENT ->
          new InvalidQueryException(
              "Invalid inference request: " + e.getStatus().getDescription(), e);
      case DEADLINE_EXCEEDED ->
          new QueryTimeoutException("Inference timeout for " + schemaId + "/" + contextId, e);
      default ->
          new GraphNotAvailableException(
              "Inference error for " + schemaId + "/" + contextId + ": " + e.getStatus(), e);
    };
  }
}
