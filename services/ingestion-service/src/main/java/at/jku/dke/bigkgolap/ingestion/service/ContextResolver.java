package at.jku.dke.bigkgolap.ingestion.service;

import at.jku.dke.bigkgolap.engine.AnalyzerResult;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns an {@link AnalyzerResult} into the set of {@link Context}s that should be indexed for the
 * file — one {@link Context} per feature group surfaced by the analyzer.
 *
 * <p>Multi-feature semantics live entirely in {@link AnalyzerResult#featureContexts()}. Engines
 * that have only flat hierarchies use {@link AnalyzerResult#ofFlat}, which encodes the file as a
 * single feature group; the resolver therefore needs no separate fallback.
 */
@Component
public class ContextResolver {

  public Set<Context> resolve(AnalyzerResult result, CubeSchema schema) {
    Set<Context> contexts = new LinkedHashSet<>();
    for (var featureCtx : result.featureContexts()) {
      contexts.add(Context.of(featureCtx.values(), schema));
    }
    return contexts;
  }
}
