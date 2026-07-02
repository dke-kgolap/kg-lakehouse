package at.jku.dke.bigkgolap.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * {@code representations} lists which cache slots to wipe (as enum <em>names</em> — the wire stays
 * domain-model-free). Default is the closed set of all known representations so a
 * forward-compatible publisher (older ingestion-service) gets the original "wipe everything for
 * these contexts" semantics.
 */
public record CacheInvalidationEvent(
    String schemaId, List<String> contextIds, List<String> representations, Instant emittedAt) {

  private static final List<String> DEFAULT_REPRESENTATIONS = List.of("RDF", "LPG", "GRAPH_FRAME");

  /**
   * Jackson deserialization entry point. {@code representations} is optional on the wire; it
   * defaults to the full set of known representations.
   */
  @JsonCreator
  public static CacheInvalidationEvent jsonCreate(
      @JsonProperty("schemaId") String schemaId,
      @JsonProperty("contextIds") List<String> contextIds,
      @JsonProperty("representations") List<String> representations,
      @JsonProperty("emittedAt") Instant emittedAt) {
    if (schemaId == null) throw new MessagingDecodeException("Missing required field: schemaId");
    if (contextIds == null)
      throw new MessagingDecodeException("Missing required field: contextIds");
    if (emittedAt == null) throw new MessagingDecodeException("Missing required field: emittedAt");
    return new CacheInvalidationEvent(
        schemaId,
        contextIds,
        representations != null ? representations : DEFAULT_REPRESENTATIONS,
        emittedAt);
  }

  /** Factory overload: uses default representations and {@code emittedAt = Instant.now()}. */
  public static CacheInvalidationEvent of(String schemaId, List<String> contextIds) {
    return new CacheInvalidationEvent(schemaId, contextIds, DEFAULT_REPRESENTATIONS, Instant.now());
  }

  /** Factory overload: uses default representations with an explicit {@code emittedAt}. */
  public static CacheInvalidationEvent of(
      String schemaId, List<String> contextIds, Instant emittedAt) {
    return new CacheInvalidationEvent(schemaId, contextIds, DEFAULT_REPRESENTATIONS, emittedAt);
  }
}
