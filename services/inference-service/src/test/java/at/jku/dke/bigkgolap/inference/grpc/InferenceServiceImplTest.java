package at.jku.dke.bigkgolap.inference.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.jku.dke.bigkgolap.grpc.InferBatchRequest;
import at.jku.dke.bigkgolap.grpc.InferContext;
import at.jku.dke.bigkgolap.grpc.InferResponse;
import at.jku.dke.bigkgolap.inference.service.ContextInferenceService;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InferenceServiceImplTest {

  private ContextInferenceService inference;
  private InferenceServiceImpl service;

  @BeforeEach
  void setUp() {
    inference = mock(ContextInferenceService.class);
    when(inference.infer(eq("atm"), eq("ctx-A"), eq(""), anyList()))
        .thenReturn(
            new ContextInferenceService.InferenceResult(
                List.of("<urn:a> <urn:p> <urn:derived> ."), false));
    when(inference.infer(eq("atm"), eq("ctx-B"), eq(""), anyList()))
        .thenReturn(
            new ContextInferenceService.InferenceResult(
                List.of("<urn:b> <urn:p> <urn:derived> ."), false));
    service = new InferenceServiceImpl(inference, new SimpleMeterRegistry(), 200);
  }

  @Test
  void inferBatchTagsDerivedTriplesPerContext() {
    var responses = new ArrayList<InferResponse>();
    StreamObserver<InferResponse> obs =
        new StreamObserver<>() {
          public void onNext(InferResponse r) {
            responses.add(r);
          }

          public void onError(Throwable t) {
            throw new AssertionError(t);
          }

          public void onCompleted() {}
        };
    InferBatchRequest req =
        InferBatchRequest.newBuilder()
            .setSchemaId("atm")
            .addContexts(
                InferContext.newBuilder()
                    .setContextId("ctx-A")
                    .setEngineId("")
                    .addBaseQuads("<urn:a> <urn:p> <urn:o> <urn:g> ."))
            .addContexts(
                InferContext.newBuilder()
                    .setContextId("ctx-B")
                    .setEngineId("")
                    .addBaseQuads("<urn:b> <urn:p> <urn:o> <urn:g> ."))
            .build();

    service.inferBatch(req, obs);

    // Every chunk is tagged, and both contexts are represented.
    assertThat(responses).isNotEmpty();
    assertThat(responses).allSatisfy(r -> assertThat(r.getContextId()).isIn("ctx-A", "ctx-B"));
    assertThat(responses.stream().map(InferResponse::getContextId).distinct())
        .containsExactlyInAnyOrder("ctx-A", "ctx-B");
  }
}
