package at.jku.dke.bigkgolap.index;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link CqlSession}, waiting for Cassandra to come up rather than failing Spring context
 * initialization. On a cluster reset a service can start before the Cassandra ring resolves; the
 * driver then throws {@link AllNodesFailedException} from {@code build()} and the service
 * crash-loops until Cassandra is ready. This retries the build at a fixed interval up to a bounded
 * deadline, then gives up and rethrows. Defence-in-depth alongside the wait-for-cassandra
 * init-container.
 */
public final class CqlSessions {

  private static final Logger log = LoggerFactory.getLogger(CqlSessions.class);

  private CqlSessions() {}

  /** Build the session, retrying {@link AllNodesFailedException} for up to {@code maxWait}. */
  public static CqlSession buildWithRetry(
      CqlSessionBuilder builder, Duration maxWait, Duration interval) {
    return buildWithRetry(builder::build, maxWait, interval, Thread::sleep);
  }

  /** Package-private seam for tests: retries any session supplier, with an injectable sleeper. */
  static CqlSession buildWithRetry(
      Supplier<CqlSession> build, Duration maxWait, Duration interval, Sleeper sleeper) {
    long intervalMs = Math.max(1L, interval.toMillis());
    int maxAttempts = (int) Math.max(1L, maxWait.toMillis() / intervalMs);
    AllNodesFailedException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return build.get();
      } catch (AllNodesFailedException e) {
        last = e;
        if (attempt == maxAttempts) {
          break;
        }
        log.warn(
            "Cassandra not reachable yet (attempt {}/{}); retrying in {}s",
            attempt,
            maxAttempts,
            intervalMs / 1000.0);
        try {
          sleeper.sleep(intervalMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
    throw last;
  }

  /** Sleep abstraction so tests need not wait in real time. */
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
