package at.jku.dke.bigkgolap.observability.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;

public class OtelGrpcServerInterceptor implements ServerInterceptor {

  private final ServerInterceptor delegate;

  public OtelGrpcServerInterceptor(OpenTelemetry otel) {
    this.delegate = GrpcTelemetry.create(otel).newServerInterceptor();
  }

  @Override
  public <Q, S> ServerCall.Listener<Q> interceptCall(
      ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
    return delegate.interceptCall(call, headers, next);
  }
}
