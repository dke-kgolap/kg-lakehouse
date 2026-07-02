package at.jku.dke.bigkgolap.surface.api.dto;

import at.jku.dke.bigkgolap.index.IngestionLogEntry;
import at.jku.dke.bigkgolap.index.QueryLogEntry;

public final class LogsDto {

  private LogsDto() {}

  public record IngestionLogDto(
      String id,
      String schemaId,
      String storedName,
      String engineId,
      long analysisMs,
      long indexWriteMs,
      long totalMs,
      int contextsCount,
      String completedAt) {}

  public record QueryLogDto(
      String id,
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
      String completedAt) {}

  public static IngestionLogDto toIngestionDto(IngestionLogEntry e) {
    return new IngestionLogDto(
        e.id().toString(),
        e.schemaId(),
        e.storedName(),
        e.engineId(),
        e.analysisMs(),
        e.indexWriteMs(),
        e.totalMs(),
        e.contextsCount(),
        e.completedAt().toString());
  }

  public static QueryLogDto toQueryDto(QueryLogEntry e) {
    return new QueryLogDto(
        e.id().toString(),
        e.schemaId(),
        e.queryText(),
        e.contextResolveMs(),
        e.graphConstructMs(),
        e.mergeMs(),
        e.totalMs(),
        e.contextsCount(),
        e.quadsCount(),
        e.cacheHits(),
        e.cacheMisses(),
        e.success(),
        e.errorMessage(),
        e.completedAt().toString());
  }
}
