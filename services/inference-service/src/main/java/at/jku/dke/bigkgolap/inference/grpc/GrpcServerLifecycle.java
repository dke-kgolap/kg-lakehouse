package at.jku.dke.bigkgolap.inference.grpc;

import io.grpc.Server;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Starts/stops the gRPC {@link Server} bean (which is only built, not started, by GrpcConfig). */
@Component
@Profile("!test")
public class GrpcServerLifecycle {

  private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;

  private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

  private final Server server;

  public GrpcServerLifecycle(Server server) {
    this.server = server;
  }

  @PostConstruct
  public void start() throws Exception {
    server.start();
    log.info("gRPC server listening on port {}", server.getPort());
  }

  @PreDestroy
  public void stop() throws InterruptedException {
    log.info("Shutting down gRPC server on port {}", server.getPort());
    server.shutdown().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }
}
