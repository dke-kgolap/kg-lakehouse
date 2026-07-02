package at.jku.dke.bigkgolap.surface.api.dto;

public record IngestionResponse(
    String schemaId,
    String storedName,
    String taskId,
    String engineId,
    String originalName,
    long sizeBytes,
    String traceId) {}
