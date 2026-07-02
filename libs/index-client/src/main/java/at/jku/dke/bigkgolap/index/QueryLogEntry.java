package at.jku.dke.bigkgolap.index;

import java.time.Instant;
import java.util.UUID;

public record QueryLogEntry(
    UUID id,
    String schemaId,
    String queryText,
    long contextResolveMs,
    long graphConstructMs,
    long mergeMs,
    long totalMs,
    int contextsCount,
    long quadsCount,
    int cacheHits,
    int cacheMisses,
    boolean success,
    String errorMessage,
    Instant completedAt) {}
