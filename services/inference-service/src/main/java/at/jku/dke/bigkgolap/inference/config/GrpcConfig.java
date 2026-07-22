package at.jku.dke.bigkgolap.inference.config;

import at.jku.dke.bigkgolap.inference.grpc.InferenceServiceImpl;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class GrpcConfig {

  private static final int MAX_INBOUND_MESSAGE_BYTES = 64 * 1024 * 1024;

  @Bean(destroyMethod = "shutdown")
  public Server grpcServer(
      InferenceServiceImpl impl,
      LakehouseProperties props,
      ObjectProvider<ServerInterceptor> serverInterceptor) {
    NettyServerBuilder builder =
        NettyServerBuilder.forPort(props.grpc().port())
            .maxInboundMessageSize(MAX_INBOUND_MESSAGE_BYTES)
            .keepAliveTime(5, TimeUnit.MINUTES)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            // permit the clients' 60s keepalive cadence: without this, Netty's 5-minute
            // default treats their pings as too_many_pings and GOAWAYs the channel, which
            // flips the clients' channel-gated readiness and drops their headless DNS
            .permitKeepAliveTime(30, TimeUnit.SECONDS)
            .permitKeepAliveWithoutCalls(true)
            .addService(impl);
    serverInterceptor.ifAvailable(builder::intercept);
    return builder.build();
  }
}
