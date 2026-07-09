package at.jku.dke.bigkgolap.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CqlSessionsTest {

  private static final CqlSessions.Sleeper NO_SLEEP = millis -> {};

  private static AllNodesFailedException cassandraDown() {
    return AllNodesFailedException.fromErrors(Map.of());
  }

  @Test
  void retriesUntilCassandraIsUp() {
    AtomicInteger attempts = new AtomicInteger();
    Supplier<CqlSession> build =
        () -> {
          if (attempts.incrementAndGet() < 3) {
            throw cassandraDown();
          }
          return null; // "up" — return whatever the driver would hand back
        };
    CqlSession session =
        CqlSessions.buildWithRetry(build, Duration.ofSeconds(30), Duration.ofSeconds(1), NO_SLEEP);
    assertNull(session);
    assertEquals(3, attempts.get(), "should retry twice then succeed on the third build");
  }

  @Test
  void givesUpAfterTheDeadlineAndRethrows() {
    AtomicInteger attempts = new AtomicInteger();
    Supplier<CqlSession> build =
        () -> {
          attempts.incrementAndGet();
          throw cassandraDown();
        };
    assertThrows(
        AllNodesFailedException.class,
        () ->
            CqlSessions.buildWithRetry(
                build, Duration.ofSeconds(3), Duration.ofSeconds(1), NO_SLEEP));
    assertEquals(3, attempts.get(), "maxWait 3s / interval 1s = 3 attempts before giving up");
  }
}
