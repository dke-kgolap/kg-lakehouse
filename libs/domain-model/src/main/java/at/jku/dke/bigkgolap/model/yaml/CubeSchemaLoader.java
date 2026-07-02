package at.jku.dke.bigkgolap.model.yaml;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Dimension;
import at.jku.dke.bigkgolap.model.InvalidCubeSchemaException;
import at.jku.dke.bigkgolap.model.Level;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class CubeSchemaLoader {

  private CubeSchemaLoader() {}

  public static CubeSchema load(InputStream input) {
    var yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    Object raw = yaml.load(input);
    if (!(raw instanceof Map<?, ?> rootMap)) {
      throw new InvalidCubeSchemaException(
          "YAML root must be a mapping with a 'schema:' key (was %s)"
              .formatted(raw == null ? "null" : raw.getClass().getSimpleName()));
    }
    Object schemaNode = rootMap.get("schema");
    if (schemaNode == null) {
      throw new InvalidCubeSchemaException("YAML must have a top-level 'schema:' key");
    }
    if (!(schemaNode instanceof Map<?, ?> schemaMap)) {
      throw new InvalidCubeSchemaException("'schema' must be a mapping");
    }

    String id = requireStr(schemaMap, "id", null);

    Object dimensionsNode = schemaMap.get("dimensions");
    if (dimensionsNode == null) {
      throw new InvalidCubeSchemaException(
          "Schema '%s': missing 'dimensions' section".formatted(id));
    }
    if (!(dimensionsNode instanceof Map<?, ?> dimensionsMap)) {
      throw new InvalidCubeSchemaException(
          "Schema '%s': 'dimensions' must be a mapping".formatted(id));
    }

    Map<?, ?> hierarchiesNode = null;
    Object h = schemaMap.get("hierarchies");
    if (h != null) {
      if (!(h instanceof Map<?, ?>)) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': 'hierarchies' must be a mapping".formatted(id));
      }
      hierarchiesNode = (Map<?, ?>) h;
    }

    var dimensions = new LinkedHashMap<String, Dimension>();
    for (var dimEntry : dimensionsMap.entrySet()) {
      Object dimKey = dimEntry.getKey();
      if (!(dimKey instanceof String dimName)) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': dimension keys must be strings (got %s)".formatted(id, dimKey));
      }
      Object dimVal = dimEntry.getValue();
      if (!(dimVal instanceof Map<?, ?> dimMap)) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': dimension '%s' must be a mapping".formatted(id, dimName));
      }
      Object levelsNode = dimMap.get("levels");
      if (levelsNode == null) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': dimension '%s' is missing 'levels'".formatted(id, dimName));
      }
      if (!(levelsNode instanceof List<?> levelsList)) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': dimension '%s': 'levels' must be a list".formatted(id, dimName));
      }
      var levels = new ArrayList<Level>();
      for (int i = 0; i < levelsList.size(); i++) {
        levels.add(parseLevel(id, dimName, i, levelsList.get(i)));
      }
      List<Map<String, String>> hierarchyData =
          parseHierarchyRows(
              id, dimName, hierarchiesNode != null ? hierarchiesNode.get(dimName) : null);
      dimensions.put(dimName, new Dimension(dimName, levels, hierarchyData));
    }

    if (hierarchiesNode != null) {
      for (Object key : hierarchiesNode.keySet()) {
        if (!dimensions.containsKey(key)) {
          throw new InvalidCubeSchemaException(
              "Schema '%s': hierarchies declared for unknown dimension '%s'".formatted(id, key));
        }
      }
    }

    return new CubeSchema(id, dimensions);
  }

  private static Level parseLevel(String schemaId, String dimName, int index, Object node) {
    if (!(node instanceof Map<?, ?> nodeMap)) {
      throw new InvalidCubeSchemaException(
          "Schema '%s': dimension '%s' level #%d must be a mapping"
              .formatted(schemaId, dimName, index));
    }
    String name =
        requireStr(
            nodeMap,
            "name",
            "Schema '%s': dimension '%s' level #%d missing 'name'"
                .formatted(schemaId, dimName, index));
    int depth =
        requireInt(
            nodeMap,
            "depth",
            "Schema '%s': level '%s.%s' missing 'depth'".formatted(schemaId, dimName, name));
    String rollupTo = optStr(nodeMap, "rollup_to");
    String rollupFn = optStr(nodeMap, "rollup_function");
    return new Level(name, dimName, depth, rollupTo, rollupFn);
  }

  private static List<Map<String, String>> parseHierarchyRows(
      String schemaId, String dimName, Object node) {
    if (node == null) return List.of();
    if (!(node instanceof List<?> list)) {
      throw new InvalidCubeSchemaException(
          "Schema '%s': hierarchies for '%s' must be a list".formatted(schemaId, dimName));
    }
    var result = new ArrayList<Map<String, String>>();
    for (int i = 0; i < list.size(); i++) {
      Object row = list.get(i);
      if (!(row instanceof Map<?, ?> rowMap)) {
        throw new InvalidCubeSchemaException(
            "Schema '%s': hierarchy row #%d for '%s' must be a mapping"
                .formatted(schemaId, i, dimName));
      }
      var rowOut = new LinkedHashMap<String, String>();
      for (var entry : rowMap.entrySet()) {
        Object k = entry.getKey();
        if (!(k instanceof String key)) {
          throw new InvalidCubeSchemaException(
              "Schema '%s': hierarchy row #%d for '%s' has non-string key '%s'"
                  .formatted(schemaId, i, dimName, k));
        }
        Object v = entry.getValue();
        if (v == null) {
          throw new InvalidCubeSchemaException(
              "Schema '%s': hierarchy row #%d for '%s' has null value for key '%s'"
                  .formatted(schemaId, i, dimName, key));
        }
        rowOut.put(key, v.toString());
      }
      result.add(Map.copyOf(rowOut));
    }
    return List.copyOf(result);
  }

  private static String requireStr(Map<?, ?> node, String key, String message) {
    Object v = node.get(key);
    if (v == null) {
      throw new InvalidCubeSchemaException(
          message != null ? message : "Missing required key '%s'".formatted(key));
    }
    if (!(v instanceof String s)) {
      throw new InvalidCubeSchemaException(
          "Value for '%s' must be a string (was %s)".formatted(key, v.getClass().getSimpleName()));
    }
    return s;
  }

  private static int requireInt(Map<?, ?> node, String key, String message) {
    Object v = node.get(key);
    if (v == null) throw new InvalidCubeSchemaException(message);
    if (v instanceof Integer i) return i;
    if (v instanceof Long l) return l.intValue();
    if (v instanceof Number n) return n.intValue();
    if (v instanceof String s) {
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        throw new InvalidCubeSchemaException(
            "Value for '%s' must be an integer (was '%s')".formatted(key, s));
      }
    }
    throw new InvalidCubeSchemaException(
        "Value for '%s' must be an integer (was %s)".formatted(key, v.getClass().getSimpleName()));
  }

  private static String optStr(Map<?, ?> node, String key) {
    Object v = node.get(key);
    if (v == null) return null;
    if (!(v instanceof String s)) {
      throw new InvalidCubeSchemaException(
          "Value for '%s' must be a string or null (was %s)"
              .formatted(key, v.getClass().getSimpleName()));
    }
    return s;
  }
}
