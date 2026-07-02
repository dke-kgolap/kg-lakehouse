package at.jku.dke.bigkgolap.observability.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;

public class OtelGrpcClientInterceptor implements ClientInterceptor {

  private final ClientInterceptor delegate;

  public OtelGrpcClientInterceptor(OpenTelemetry otel) {
    this.delegate = GrpcTelemetry.create(otel).newClientInterceptor();
  }

  @Override
  public <Q, S> ClientCall<Q, S> interceptCall(
      MethodDescriptor<Q, S> method, CallOptions callOptions, Channel next) {
    return delegate.interceptCall(method, callOptions, next);
  }
}
