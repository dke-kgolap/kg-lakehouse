package at.jku.dke.bigkgolap.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.grpc.InferBatchRequest;
import at.jku.dke.bigkgolap.grpc.InferResponse;
import at.jku.dke.bigkgolap.grpc.InferenceServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.junit.jupiter.api.Test;

class InferenceServiceClientTest {

  @Test
  void inferBatchGroupsDerivedTriplesByContext() throws Exception {
    String name = InProcessServerBuilder.generateName();
    var impl =
        new InferenceServiceGrpc.InferenceServiceImplBase() {
          @Override
          public void inferBatch(InferBatchRequest req, StreamObserver<InferResponse> obs) {
            for (var c : req.getContextsList()) {
              obs.onNext(
                  InferResponse.newBuilder()
                      .setContextId(c.getContextId())
                      .addDerivedTriples("<urn:" + c.getContextId() + "> <urn:t> <urn:Super> .")
                      .build());
            }
            obs.onCompleted();
          }
        };
    Server server =
        InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start();
    ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    var client = new InferenceServiceClient(InferenceServiceGrpc.newBlockingStub(channel), 5L);

    var out =
        client.inferBatch(
            "atm",
            List.of(
                new InferenceServiceClient.InferContextSpec(
                    "ctx-A", "", List.of("<urn:a> <urn:p> <urn:o> <urn:g> .")),
                new InferenceServiceClient.InferContextSpec(
                    "ctx-B", "", List.of("<urn:b> <urn:p> <urn:o> <urn:g> ."))));

    assertThat(out.keySet()).containsExactlyInAnyOrder("ctx-A", "ctx-B");
    assertThat(out.get("ctx-A")).containsExactly("<urn:ctx-A> <urn:t> <urn:Super> .");
    assertThat(out.get("ctx-B")).containsExactly("<urn:ctx-B> <urn:t> <urn:Super> .");
    channel.shutdownNow();
    server.shutdownNow();
  }
}
