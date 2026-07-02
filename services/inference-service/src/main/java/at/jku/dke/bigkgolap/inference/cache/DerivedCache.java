package at.jku.dke.bigkgolap.inference.cache;

import java.util.List;
import java.util.Optional;

/**
 * Caches the derived (inferred) triples of a context, keyed by {@code (schemaId, contextId,
 * tboxVersion)}. The {@code tboxVersion} component invalidates automatically when the schema's
 * generated TBox or the engine's supplementary ontology changes.
 */
public interface DerivedCache {

  Optional<List<String>> get(String key);

  void put(String key, List<String> derivedTriples);
}
