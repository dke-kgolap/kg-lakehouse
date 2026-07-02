package at.jku.dke.bigkgolap.surface.api.dto;

import at.jku.dke.bigkgolap.model.CubeSchema;
import java.util.List;

public record SchemaResponse(String id, List<DimensionResponse> dimensions) {

  public record DimensionResponse(String name, List<LevelResponse> levels) {}

  public record LevelResponse(String name, int depth, String rollupTo, String rollupFunction) {}

  public static SchemaResponse from(CubeSchema schema) {
    var dimensions =
        schema.dimensions().values().stream()
            .map(
                dim ->
                    new DimensionResponse(
                        dim.name(),
                        dim.levels().stream()
                            .map(
                                level ->
                                    new LevelResponse(
                                        level.name(),
                                        level.depth(),
                                        level.rollupTo(),
                                        level.rollupFunction()))
                            .toList()))
            .toList();
    return new SchemaResponse(schema.id(), dimensions);
  }
}
