package at.jku.dke.bigkgolap.messaging.internal;

import at.jku.dke.bigkgolap.messaging.CacheInvalidationEvent;
import at.jku.dke.bigkgolap.messaging.IngestionCompletedEvent;
import at.jku.dke.bigkgolap.messaging.IngestionTask;
import at.jku.dke.bigkgolap.messaging.MessagingDecodeException;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class JsonCodecTest {

  @Test
  void ingestionTaskRoundTrip() {
    var original =
        new IngestionTask(
            "atm", "uuid_file.xml", "aixm", "file.xml", 0L, Instant.parse("2026-04-26T10:00:00Z"));
    byte[] bytes = JsonCodec.encode(original);
    var decoded = JsonCodec.decode(bytes, IngestionTask.class);
    Assertions.assertThat(decoded).isEqualTo(original);
  }

  @Test
  void ingestionCompletedEventRoundTrip() {
    var original =
        new IngestionCompletedEvent(
            "atm", "uuid_file.xml", "aixm", 3, 42L, Instant.parse("2026-04-26T10:00:01Z"));
    byte[] bytes = JsonCodec.encode(original);
    var decoded = JsonCodec.decode(bytes, IngestionCompletedEvent.class);
    Assertions.assertThat(decoded).isEqualTo(original);
  }

  @Test
  void cacheInvalidationEventRoundTrip() {
    var original =
        new CacheInvalidationEvent(
            "atm",
            List.of("ctx1", "ctx2"),
            List.of("RDF", "LPG", "GRAPH_FRAME"),
            Instant.parse("2026-04-26T10:00:02Z"));
    byte[] bytes = JsonCodec.encode(original);
    var decoded = JsonCodec.decode(bytes, CacheInvalidationEvent.class);
    Assertions.assertThat(decoded).isEqualTo(original);
  }

  @Test
  void decodeToleratesExtraFieldsForForwardCompatibility() {
    byte[] withExtra =
        """
                {"schemaId":"atm","storedName":"f","engineId":"aixm","originalName":"f.xml",
                 "timestamp":"2026-04-26T10:00:00Z","futureField":"ignored"}
                """
            .stripIndent()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var decoded = JsonCodec.decode(withExtra, IngestionTask.class);
    Assertions.assertThat(decoded.schemaId()).isEqualTo("atm");
  }

  @Test
  void decodeRaisesMessagingDecodeExceptionOnMalformedJson() {
    byte[] bad = "{not json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Assertions.assertThatThrownBy(() -> JsonCodec.decode(bad, IngestionTask.class))
        .isInstanceOf(MessagingDecodeException.class);
  }

  @Test
  void decodeRaisesMessagingDecodeExceptionOnMissingRequiredFields() {
    byte[] incomplete = "{\"schemaId\":\"atm\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Assertions.assertThatThrownBy(() -> JsonCodec.decode(incomplete, IngestionTask.class))
        .isInstanceOf(MessagingDecodeException.class);
  }

  @Test
  void instantIsSerializedAsIso8601StringNotNumeric() {
    var task =
        new IngestionTask("atm", "f", "aixm", "o", 0L, Instant.parse("2026-04-26T10:00:00Z"));
    String json = new String(JsonCodec.encode(task), java.nio.charset.StandardCharsets.UTF_8);
    Assertions.assertThat(json).contains("2026-04-26T10:00:00Z");
    Assertions.assertThat(json).doesNotContain("1714125600"); // would be epoch-seconds form
  }
}
