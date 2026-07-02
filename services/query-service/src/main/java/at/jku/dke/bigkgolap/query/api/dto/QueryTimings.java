package at.jku.dke.bigkgolap.query.api.dto;

public record QueryTimings(
    long contextResolutionMs,
    long mergeMs,
    long graphConstructionMs,
    long totalMs,
    int cacheHits,
    int cacheMisses) {}
