package at.jku.dke.bigkgolap.query.config;

import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.grpc.InferenceServiceGrpc;
import at.jku.dke.bigkgolap.query.grpclb.LeastRequestLoadBalancerProvider;
import io.grpc.ClientInterceptor;
import io.grpc.LoadBalancerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class GrpcClientConfig {

  private static final int MAX_INBOUND_MESSAGE_BYTES = 64 * 1024 * 1024;

  static {
    // Register the in-process P2C least-request policy so it can be selected by name. Idempotent:
    // the registry keeps the highest-priority provider per name, and re-registering is harmless.
    LoadBalancerRegistry.getDefaultRegistry().register(new LeastRequestLoadBalancerProvider());
  }

  /**
   * gRPC load-balancing policy for the graph/inference channels. Defaults to the load-aware {@code
   * least_request_p2c} (power-of-two-choices); set {@code
   * LAKEHOUSE_QUERY_GRAPH_LB_POLICY=round_robin} to fall back to plain round-robin (e.g. for A/B
   * comparison).
   */
  @Value("${lakehouse.query.graph.lb-policy:least_request_p2c}")
  private String lbPolicy;

  @Bean(destroyMethod = "shutdown")
  public ManagedChannel graphServiceChannel(
      LakehouseProperties props, ObjectProvider<ClientInterceptor> clientInterceptor) {
    return loadBalancedChannel(
        props.graph().grpc().host(), props.graph().grpc().port(), clientInterceptor);
  }

  /**
   * Builds a gRPC channel that load-balances across all backend pods. {@code forAddress} + default
   * {@code pick_first} pins every call to a single pod (a ClusterIP balances connections, not
   * HTTP/2 streams), so replicas added behind a headless Service never receive traffic. Using the
   * {@code dns:///} target so the resolver returns every pod IP, plus a multi-backend policy,
   * spreads streams across all of them. The default {@code least_request_p2c} routes by
   * outstanding-stream load (round_robin distributes picks uniformly but, under the wide blocking
   * fan-out, still let a couple of pods saturate while the rest idled). Requires the target Service
   * to be headless ({@code clusterIP: None}); against a normal ClusterIP it harmlessly degrades to
   * one address.
   */
  private ManagedChannel loadBalancedChannel(
      String host, int port, ObjectProvider<ClientInterceptor> clientInterceptor) {
    NettyChannelBuilder builder =
        NettyChannelBuilder.forTarget("dns:///" + host + ":" + port)
            .defaultLoadBalancingPolicy(lbPolicy)
            .usePlaintext()
            .maxInboundMessageSize(MAX_INBOUND_MESSAGE_BYTES)
            .keepAliveTime(60, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true);
    clientInterceptor.ifAvailable(builder::intercept);
    return builder.build();
  }

  @Bean
  public GraphQueryServiceGrpc.GraphQueryServiceBlockingStub graphServiceStub(
      @Qualifier("graphServiceChannel") ManagedChannel channel) {
    return GraphQueryServiceGrpc.newBlockingStub(channel);
  }

  @Bean(destroyMethod = "shutdown")
  public ManagedChannel inferenceServiceChannel(
      LakehouseProperties props, ObjectProvider<ClientInterceptor> clientInterceptor) {
    return loadBalancedChannel(
        props.inference().grpc().host(), props.inference().grpc().port(), clientInterceptor);
  }

  @Bean
  public InferenceServiceGrpc.InferenceServiceBlockingStub inferenceServiceStub(
      @Qualifier("inferenceServiceChannel") ManagedChannel channel) {
    return InferenceServiceGrpc.newBlockingStub(channel);
  }
}
