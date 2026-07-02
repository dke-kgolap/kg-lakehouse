package at.jku.dke.bigkgolap.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class KafkaMessagingServiceKeyTest {
  private static final IngestionTask TASK =
      new IngestionTask("atm", "abc123_baseline.xml", "aixm", "baseline.xml", 10L, Instant.EPOCH);

  @Test
  void defaultsToSchemaIdKey() {
    assertEquals("atm", KafkaMessagingService.ingestionKey(TASK, false));
  }

  @Test
  void fileStrategyKeysByStoredName() {
    assertEquals("abc123_baseline.xml", KafkaMessagingService.ingestionKey(TASK, true));
  }
}
