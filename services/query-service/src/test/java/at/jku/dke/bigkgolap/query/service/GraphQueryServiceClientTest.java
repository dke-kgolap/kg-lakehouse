package at.jku.dke.bigkgolap.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.grpc.GraphCacheRequest;
import at.jku.dke.bigkgolap.grpc.GraphCacheResponse;
import at.jku.dke.bigkgolap.grpc.GraphQueryRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryResponse;
import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.query.exception.GraphNotAvailableException;
import at.jku.dke.bigkgolap.query.exception.InvalidQueryException;
import at.jku.dke.bigkgolap.query.exception.QueryTimeoutException;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GraphQueryServiceClientTest {

  private static final long DEFAULT_TIMEOUT = 5L;

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() {
    if (channel != null) channel.shutdownNow();
    if (server != null) server.shutdownNow();
  }

  @Test
  void happyPathCollectsAllStreamedQuads() {
    GraphQueryServiceClient client =
        startWithImpl(
            new GraphQueryServiceGrpc.GraphQueryServiceImplBase() {
              @Override
              public void queryGraph(
                  GraphQueryRequest req, StreamObserver<GraphQueryResponse> observer) {
                observer.onNext(
                    GraphQueryResponse.newBuilder().addQuads("<a> <b> <c> <g> .").build());
                observer.onNext(
                    GraphQueryResponse.newBuilder().addQuads("<d> <e> <f> <g> .").build());
                observer.onCompleted();
              }
            });
    var result = client.queryQuads("atm", "ctx-1", List.of("g"), GraphRepresentation.RDF);
    assertThat(result.lines()).hasSize(2);
    assertThat(result.fromCache()).isFalse();
  }

  @Test
  void notFoundMapsToGraphNotAvailableException() {
    GraphQueryServiceClient client =
        startWithImpl(
            new GraphQueryServiceGrpc.GraphQueryServiceImplBase() {
              @Override
              public void queryGraph(
                  GraphQueryRequest req, StreamObserver<GraphQueryResponse> observer) {
                observer.onError(Status.NOT_FOUND.asRuntimeException());
              }
            });
    assertThatThrownBy(
            () -> client.queryQuads("atm", "missing", List.of("g"), GraphRepresentation.RDF))
        .isInstanceOf(GraphNotAvailableException.class);
  }

  @Test
  void unimplementedMapsToInvalidQueryException() {
    GraphQueryServiceClient client =
        startWithImpl(
            new GraphQueryServiceGrpc.GraphQueryServiceImplBase() {
              @Override
              public void queryGraph(
                  GraphQueryRequest req, StreamObserver<GraphQueryResponse> observer) {
                observer.onError(Status.UNIMPLEMENTED.asRuntimeException());
              }
            });
    assertThatThrownBy(() -> client.queryQuads("atm", "x", List.of("g"), GraphRepresentation.LPG))
        .isInstanceOf(InvalidQueryException.class);
  }

  @Test
  void deadlineExceededMapsToQueryTimeoutException() {
    GraphQueryServiceClient client =
        startWithImpl(
            new GraphQueryServiceGrpc.GraphQueryServiceImplBase() {
              @Override
              public void queryGraph(
                  GraphQueryRequest req, StreamObserver<GraphQueryResponse> observer) {
                observer.onError(Status.DEADLINE_EXCEEDED.asRuntimeException());
              }
            },
            1L);
    assertThatThrownBy(() -> client.queryQuads("atm", "x", List.of("g"), GraphRepresentation.RDF))
        .isInstanceOf(QueryTimeoutException.class);
  }

  @Test
  void emptyGraphUrisIsRejectedBeforeContactingServer() {
    GraphQueryServiceClient client =
        startWithImpl(
            new GraphQueryServiceGrpc.GraphQueryServiceImplBase() {
              @Override
              public void queryGraph(
                  GraphQueryRequest req, StreamObserver<GraphQueryResponse> observer) {
                observer.onCompleted();
              }

              @Override
              public void clearCache(
                  GraphCacheRequest req, StreamObserver<GraphCacheResponse> observer) {
                observer.onCompleted();
              }
            });
    assertThatThrownBy(() -> client.queryQuads("atm", "x", List.of(), GraphRepresentation.RDF))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private GraphQueryServiceClient startWithImpl(
      GraphQueryServiceGrpc.GraphQueryServiceImplBase impl) {
    return startWithImpl(impl, DEFAULT_TIMEOUT);
  }

  private GraphQueryServiceClient startWithImpl(
      GraphQueryServiceGrpc.GraphQueryServiceImplBase impl, long timeoutSeconds) {
    String name = InProcessServerBuilder.generateName();
    try {
      server =
          InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start();
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    var stub = GraphQueryServiceGrpc.newBlockingStub(channel);
    return new GraphQueryServiceClient(stub, timeoutSeconds);
  }
}
