package at.jku.dke.bigkgolap.index;

import java.time.Instant;
import java.util.UUID;

public record IngestionLogEntry(
    UUID id,
    String schemaId,
    String storedName,
    String engineId,
    long analysisMs,
    long indexWriteMs,
    long totalMs,
    int contextsCount,
    Instant completedAt) {}
