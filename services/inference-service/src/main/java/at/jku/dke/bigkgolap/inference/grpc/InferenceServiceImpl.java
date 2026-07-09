package at.jku.dke.bigkgolap.inference.grpc;

import at.jku.dke.bigkgolap.grpc.InferBatchRequest;
import at.jku.dke.bigkgolap.grpc.InferContext;
import at.jku.dke.bigkgolap.grpc.InferRequest;
import at.jku.dke.bigkgolap.grpc.InferResponse;
import at.jku.dke.bigkgolap.grpc.InferenceServiceGrpc;
import at.jku.dke.bigkgolap.inference.service.ContextInferenceService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InferenceServiceImpl extends InferenceServiceGrpc.InferenceServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(InferenceServiceImpl.class);

  private final ContextInferenceService inference;
  private final MeterRegistry meterRegistry;
  private final int chunkTarget;

  public InferenceServiceImpl(
      ContextInferenceService inference,
      MeterRegistry meterRegistry,
      @Value("${lakehouse.inference.chunk.target-triples:200}") int chunkTarget) {
    this.inference = inference;
    this.meterRegistry = meterRegistry;
    this.chunkTarget = chunkTarget;
  }

  @Override
  public void infer(InferRequest request, StreamObserver<InferResponse> observer) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      ContextInferenceService.InferenceResult result =
          inference.infer(
              request.getSchemaId(),
              request.getContextId(),
              request.getEngineId(),
              request.getBaseQuadsList());
      stream(result.derivedTriples(), result.fromCache(), request.getContextId(), observer);
      observer.onCompleted();
      log.info(
          "infer schema={} context={} engine={} derived={} fromCache={}",
          request.getSchemaId(),
          request.getContextId(),
          request.getEngineId(),
          result.derivedTriples().size(),
          result.fromCache());
    } catch (IllegalArgumentException e) {
      observer.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (RuntimeException e) {
      log.error("infer failed for {}/{}", request.getSchemaId(), request.getContextId(), e);
      observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    } finally {
      sample.stop(
          meterRegistry.timer("lakehouse.inference.duration", "schema", request.getSchemaId()));
    }
  }

  @Override
  public void inferBatch(InferBatchRequest request, StreamObserver<InferResponse> observer) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      for (InferContext c : request.getContextsList()) {
        ContextInferenceService.InferenceResult result =
            inference.infer(
                request.getSchemaId(), c.getContextId(), c.getEngineId(), c.getBaseQuadsList());
        stream(result.derivedTriples(), result.fromCache(), c.getContextId(), observer);
      }
      observer.onCompleted();
      log.info(
          "inferBatch schema={} contexts={}", request.getSchemaId(), request.getContextsCount());
    } catch (IllegalArgumentException e) {
      observer.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (RuntimeException e) {
      log.error("inferBatch failed for {}", request.getSchemaId(), e);
      observer.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    } finally {
      sample.stop(
          meterRegistry.timer("lakehouse.inference.duration", "schema", request.getSchemaId()));
    }
  }

  private void stream(
      List<String> triples,
      boolean fromCache,
      String contextId,
      StreamObserver<InferResponse> observer) {
    InferResponse.Builder builder = InferResponse.newBuilder().setContextId(contextId);
    int inChunk = 0;
    for (String triple : triples) {
      builder.addDerivedTriples(triple);
      if (++inChunk >= chunkTarget) {
        observer.onNext(builder.build());
        builder = InferResponse.newBuilder().setContextId(contextId);
        inChunk = 0;
      }
    }
    // Final message (possibly empty) carries from_cache, mirroring graph-service's convention.
    observer.onNext(builder.setFromCache(fromCache).build());
  }
}
