package at.jku.dke.bigkgolap.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record IngestionTask(
    String schemaId,
    String storedName,
    String engineId,
    String originalName,
    long sizeBytes,
    Instant timestamp) {

  /**
   * Jackson deserialization entry point. {@code sizeBytes} is optional on the wire (older
   * publishers omit it); it defaults to {@code 0}. Required fields are checked explicitly so that
   * missing ones surface as {@link at.jku.dke.bigkgolap.messaging.MessagingDecodeException} rather
   * than silently producing {@code null}-filled records.
   */
  @JsonCreator
  public static IngestionTask jsonCreate(
      @JsonProperty("schemaId") String schemaId,
      @JsonProperty("storedName") String storedName,
      @JsonProperty("engineId") String engineId,
      @JsonProperty("originalName") String originalName,
      @JsonProperty("sizeBytes") Long sizeBytes,
      @JsonProperty("timestamp") Instant timestamp) {
    requireField(schemaId, "schemaId");
    requireField(storedName, "storedName");
    requireField(engineId, "engineId");
    requireField(originalName, "originalName");
    requireField(timestamp, "timestamp");
    return new IngestionTask(
        schemaId,
        storedName,
        engineId,
        originalName,
        sizeBytes != null ? sizeBytes : 0L,
        timestamp);
  }

  private static void requireField(Object value, String name) {
    if (value == null) {
      throw new MessagingDecodeException("Missing required field: " + name);
    }
  }

  /** Factory overload: {@code sizeBytes = 0} and {@code timestamp = Instant.now()}. */
  public static IngestionTask of(
      String schemaId, String storedName, String engineId, String originalName) {
    return new IngestionTask(schemaId, storedName, engineId, originalName, 0L, Instant.now());
  }

  /** Factory overload: {@code sizeBytes = 0}, explicit timestamp. */
  public static IngestionTask of(
      String schemaId, String storedName, String engineId, String originalName, Instant timestamp) {
    return new IngestionTask(schemaId, storedName, engineId, originalName, 0L, timestamp);
  }
}
