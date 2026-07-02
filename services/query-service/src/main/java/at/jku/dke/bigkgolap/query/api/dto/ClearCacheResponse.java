package at.jku.dke.bigkgolap.query.api.dto;

import java.util.List;

public record ClearCacheResponse(List<String> clearedSchemas) {}
