package at.jku.dke.bigkgolap.messaging;

import java.time.Instant;

public record IngestionCompletedEvent(
    String schemaId,
    String storedName,
    String engineId,
    int contextsCount,
    long totalMs,
    Instant completedAt) {}
