package at.jku.dke.bigkgolap.query.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class ExecutorConfig {

  @Bean(name = "graphFanoutExecutor", destroyMethod = "shutdown")
  public ExecutorService graphFanoutExecutor(LakehouseProperties props, MeterRegistry registry) {
    int size = props.query().fanout().poolSize();
    AtomicLong counter = new AtomicLong();
    ThreadFactory factory =
        runnable -> {
          Thread t = new Thread(runnable, "graph-fanout-" + counter.incrementAndGet());
          t.setDaemon(true);
          return t;
        };
    ExecutorService raw =
        new ThreadPoolExecutor(
            size,
            size,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(200),
            factory,
            new ThreadPoolExecutor.CallerRunsPolicy());
    return ExecutorServiceMetrics.monitor(registry, raw, "graph-fanout");
  }
}
