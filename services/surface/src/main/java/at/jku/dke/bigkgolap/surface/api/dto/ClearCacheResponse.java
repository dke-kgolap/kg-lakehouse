package at.jku.dke.bigkgolap.surface.api.dto;

import java.util.List;

public record ClearCacheResponse(List<String> clearedSchemas) {

  public ClearCacheResponse() {
    this(List.of());
  }
}
