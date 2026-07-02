package at.jku.dke.bigkgolap.engine;

import at.jku.dke.bigkgolap.model.Hierarchy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Result of {@link Analyzer#analyze}.
 *
 * <p>{@link #featureContexts()} carries per-feature grouping for engines that produce multiple
 * contexts from one input (e.g. AIXM {@code BasicMessage} with multiple {@code <message:hasMember>}
 * blocks). Each map entry binds a dimension name to its hierarchy for that feature.
 *
 * <p>{@link #hierarchies()} is the flat union derived from {@link #featureContexts()} — kept for
 * engines and tests that don't care about per-feature grouping.
 *
 * <p>Engines that have a single feature per file populate {@link #featureContexts()} with one
 * entry. Engines that don't track feature grouping at all leave {@link #featureContexts()} empty;
 * downstream code treats that as "construct one context via cartesian product across the flat
 * hierarchies".
 */
public final class AnalyzerResult {

  public static final AnalyzerResult EMPTY = new AnalyzerResult(List.of(), Map.of());

  private final List<Map<String, Hierarchy>> featureContexts;
  private final Map<String, String> metadata;

  public AnalyzerResult(
      List<Map<String, Hierarchy>> featureContexts, Map<String, String> metadata) {
    this.featureContexts = List.copyOf(featureContexts);
    this.metadata = Map.copyOf(metadata);
  }

  /** Constructs an empty result with no feature contexts and no metadata. */
  public AnalyzerResult() {
    this(List.of(), Map.of());
  }

  public List<Map<String, Hierarchy>> featureContexts() {
    return featureContexts;
  }

  public Map<String, String> metadata() {
    return metadata;
  }

  /** Returns the flat union of all hierarchies across all feature contexts. */
  public Set<Hierarchy> hierarchies() {
    var result = new HashSet<Hierarchy>();
    for (var ctx : featureContexts) {
      result.addAll(ctx.values());
    }
    return Set.copyOf(result);
  }

  /**
   * Convenience for engines that have only flat hierarchies, no per-feature grouping.'s ingestion
   * pipeline detects the empty {@code featureContexts} and falls back to cartesian-product context
   * construction.
   */
  public static AnalyzerResult ofFlat(Set<Hierarchy> hierarchies, Map<String, String> metadata) {
    if (hierarchies.isEmpty()) {
      return new AnalyzerResult(List.of(), metadata);
    }
    // Build a single feature group with all hierarchies grouped by dimension.
    // This is correct only when there is exactly one hierarchy per dimension.
    var byDim = new HashMap<String, Hierarchy>();
    for (var h : hierarchies) {
      var existing = byDim.put(h.dimension(), h);
      if (existing != null) {
        throw new IllegalArgumentException(
            "ofFlat() requires at most one hierarchy per dimension; dimension '%s' has more than one"
                .formatted(h.dimension()));
      }
    }
    return new AnalyzerResult(List.of(Map.copyOf(byDim)), metadata);
  }

  /** Overload with empty metadata. */
  public static AnalyzerResult ofFlat(Set<Hierarchy> hierarchies) {
    return ofFlat(hierarchies, Map.of());
  }

  /** Returns a copy with a different metadata map (analogous to Kotlin data class copy()). */
  public AnalyzerResult withMetadata(Map<String, String> newMetadata) {
    return new AnalyzerResult(this.featureContexts, newMetadata);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnalyzerResult other)) return false;
    return Objects.equals(featureContexts, other.featureContexts)
        && Objects.equals(metadata, other.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(featureContexts, metadata);
  }

  @Override
  public String toString() {
    return "AnalyzerResult{featureContexts=%s, metadata=%s}".formatted(featureContexts, metadata);
  }
}
