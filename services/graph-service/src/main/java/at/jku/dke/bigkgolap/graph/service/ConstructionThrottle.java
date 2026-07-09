package at.jku.dke.bigkgolap.graph.service;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounds the number of concurrent heavy graph constructions (cache-miss builds) on this pod so peak
 * memory stays commodity-sized under broad/concurrent load. Fair, blocking: excess builds queue
 * rather than fail (a wait longer than the caller's gRPC deadline surfaces as a normal timeout).
 */
@Component
public class ConstructionThrottle {

  private final Semaphore permits;

  public ConstructionThrottle(
      @Value("${lakehouse.graph.construction.max-concurrent:4}") int maxConcurrent) {
    this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
  }

  public void acquire() throws InterruptedException {
    permits.acquire();
  }

  public void release() {
    permits.release();
  }

  int availablePermits() {
    return permits.availablePermits();
  }
}
