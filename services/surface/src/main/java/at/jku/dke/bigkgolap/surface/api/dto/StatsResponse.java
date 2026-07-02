package at.jku.dke.bigkgolap.surface.api.dto;

import at.jku.dke.bigkgolap.index.LakehouseStats;

public record StatsResponse(
    String schemaId,
    long totalHierarchies,
    long totalContexts,
    long totalFiles,
    long totalStoredFileSizeBytes) {

  public static StatsResponse from(LakehouseStats stats) {
    return new StatsResponse(
        stats.schemaId(),
        stats.totalHierarchies(),
        stats.totalContexts(),
        stats.totalFiles(),
        stats.totalStoredFileSizeBytes());
  }
}
