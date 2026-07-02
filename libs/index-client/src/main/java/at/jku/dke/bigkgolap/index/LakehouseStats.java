package at.jku.dke.bigkgolap.index;

public record LakehouseStats(
    String schemaId,
    long totalHierarchies,
    long totalContexts,
    long totalFiles,
    long totalStoredFileSizeBytes) {}
