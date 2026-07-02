package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.Level;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.MergeLevels;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import at.jku.dke.bigkgolap.query.exception.InvalidQueryException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QueryParserService {

  private static final String SELECT = "SELECT ";
  private static final String ROLLUP_ON = " ROLLUP ON ";
  private static final String ALL_LEVEL = "ALL";
  private static final Pattern AND_SPLIT_REGEX =
      Pattern.compile("\\s+AND\\s+", Pattern.CASE_INSENSITIVE);

  public ParsedQuery parseText(String query, CubeSchema schema) {
    String normalized = query.strip().replace("\r\n", " ").replace("\n", " ");
    if (!normalized.toUpperCase().startsWith(SELECT)) {
      throw new InvalidQueryException("Query must start with '" + SELECT.strip() + "'");
    }
    String afterSelect = normalized.substring(SELECT.length());
    String[] parts = splitOnRollup(afterSelect);
    String selectPart = parts[0];
    String rollupPart = parts[1];
    SliceDiceContext sliceDice = parseSelect(selectPart, schema);
    MergeLevels mergeLevels = rollupPart != null ? parseRollup(rollupPart, schema) : null;
    return new ParsedQuery(sliceDice, mergeLevels);
  }

  public ParsedQuery parseStructured(
      Map<String, Map<String, String>> select,
      Map<String, String> rollup,
      GraphRepresentation representation,
      String format,
      CubeSchema schema) {
    SliceDiceContext sliceDice = sliceDiceFromMap(select, schema);
    MergeLevels mergeLevels = mergeLevelsFromMap(rollup, schema);
    return new ParsedQuery(sliceDice, mergeLevels, representation, format);
  }

  /** Returns a 2-element array: [selectPart, rollupPart or null]. */
  private String[] splitOnRollup(String afterSelect) {
    String upper = afterSelect.toUpperCase();
    int idx = upper.indexOf(ROLLUP_ON);
    if (idx < 0) {
      return new String[] {afterSelect, null};
    }
    String selectPart = afterSelect.substring(0, idx);
    String rollupPart = afterSelect.substring(idx + ROLLUP_ON.length());
    return new String[] {selectPart, rollupPart};
  }

  private SliceDiceContext parseSelect(String body, CubeSchema schema) {
    String trimmed = body.strip();
    if (trimmed.isEmpty()) {
      throw new InvalidQueryException("SELECT clause is empty");
    }
    if (trimmed.equals("*")) {
      return SliceDiceContext.empty();
    }

    String[] predicates = AND_SPLIT_REGEX.split(trimmed);
    var byDim = new LinkedHashMap<String, Hierarchy>();
    for (String rawPredicate : predicates) {
      String predicate = rawPredicate.strip();
      if (predicate.isEmpty()) continue;
      String[] lhsRhs = splitOnEquals(predicate);
      String dimLevel = lhsRhs[0];
      String value = lhsRhs[1];
      String[] dimLevelParts = splitDimLevel(dimLevel, schema);
      String dim = dimLevelParts[0];
      String levelName = dimLevelParts[1];
      Level level = schema.locate(dim, levelName);
      if (level == null) {
        throw new InvalidQueryException("Unknown level: " + dimLevel);
      }
      if (byDim.containsKey(dim)) {
        throw new InvalidQueryException("Duplicate dimension '" + dim + "' in SELECT clause");
      }
      byDim.put(dim, HierarchyFactory.create(new Member(level, value), schema));
    }
    return SliceDiceContext.of(byDim.values(), schema);
  }

  private MergeLevels parseRollup(String body, CubeSchema schema) {
    String trimmed = body.strip();
    if (trimmed.isEmpty()) {
      return new MergeLevels(Map.of());
    }
    String[] rawParts = trimmed.split(",");
    var levels = new HashMap<String, Level>();
    for (String rawPart : rawParts) {
      String part = rawPart.strip();
      if (part.isEmpty()) continue;
      String[] dimLevelParts = splitDimLevel(part, schema);
      String dim = dimLevelParts[0];
      String levelName = dimLevelParts[1];
      if (levels.containsKey(dim)) {
        throw new InvalidQueryException("Duplicate dimension '" + dim + "' in ROLLUP ON clause");
      }
      levels.put(dim, resolveRollupLevel(dim, levelName, schema));
    }
    return new MergeLevels(levels);
  }

  private SliceDiceContext sliceDiceFromMap(
      Map<String, Map<String, String>> select, CubeSchema schema) {
    if (select.isEmpty()) {
      return SliceDiceContext.empty();
    }
    var byDim = new LinkedHashMap<String, Hierarchy>();
    for (var dimEntry : select.entrySet()) {
      String dim = dimEntry.getKey();
      Map<String, String> levelToValue = dimEntry.getValue();
      if (!schema.dimensions().containsKey(dim)) {
        throw new InvalidQueryException("Unknown dimension '" + dim + "' in select");
      }
      if (levelToValue.size() != 1) {
        throw new InvalidQueryException(
            "Dimension '"
                + dim
                + "' must specify exactly one level=value (got "
                + levelToValue.size()
                + ")");
      }
      var singleEntry = levelToValue.entrySet().iterator().next();
      String levelName = singleEntry.getKey();
      String value = singleEntry.getValue();
      Level level = schema.locate(dim, levelName);
      if (level == null) {
        throw new InvalidQueryException("Unknown level '" + dim + "_" + levelName + "'");
      }
      byDim.put(dim, HierarchyFactory.create(new Member(level, value), schema));
    }
    return SliceDiceContext.of(byDim.values(), schema);
  }

  private MergeLevels mergeLevelsFromMap(Map<String, String> rollup, CubeSchema schema) {
    if (rollup.isEmpty()) {
      return null;
    }
    var levels = new HashMap<String, Level>();
    for (var dimEntry : rollup.entrySet()) {
      String dim = dimEntry.getKey();
      String levelName = dimEntry.getValue();
      if (!schema.dimensions().containsKey(dim)) {
        throw new InvalidQueryException("Unknown dimension '" + dim + "' in rollup");
      }
      levels.put(dim, resolveRollupLevel(dim, levelName, schema));
    }
    return new MergeLevels(levels);
  }

  private Level resolveRollupLevel(String dim, String levelName, CubeSchema schema) {
    if (levelName.equalsIgnoreCase(ALL_LEVEL)) {
      var dimension = schema.dimensions().get(dim);
      if (dimension == null) {
        throw new InvalidQueryException("Unknown dimension '" + dim + "'");
      }
      return dimension.rootLevel();
    }
    Level level = schema.locate(dim, levelName);
    if (level == null) {
      throw new InvalidQueryException("Unknown level '" + dim + "_" + levelName + "' in ROLLUP ON");
    }
    return level;
  }

  /** Returns [lhs, rhs]. */
  private String[] splitOnEquals(String predicate) {
    int idx = predicate.indexOf('=');
    if (idx < 0) {
      throw new InvalidQueryException(
          "Predicate '" + predicate + "' is not of the form dim_level=value");
    }
    String lhs = predicate.substring(0, idx).strip();
    String rhs = predicate.substring(idx + 1).strip();
    if (lhs.isEmpty() || rhs.isEmpty()) {
      throw new InvalidQueryException("Predicate '" + predicate + "' has empty LHS or RHS");
    }
    return new String[] {lhs, rhs};
  }

  /**
   * Resolves a {@code dim_level} token by trying each registered dimension as a prefix. This
   * handles level names containing {@code _} correctly. Returns [dim, levelName].
   */
  private String[] splitDimLevel(String token, CubeSchema schema) {
    for (String dim : schema.dimensions().keySet()) {
      String prefix = dim + "_";
      if (token.startsWith(prefix) && token.length() > prefix.length()) {
        return new String[] {dim, token.substring(prefix.length())};
      }
    }
    throw new InvalidQueryException(
        "Token '" + token + "' does not match any known dimension prefix");
  }
}
