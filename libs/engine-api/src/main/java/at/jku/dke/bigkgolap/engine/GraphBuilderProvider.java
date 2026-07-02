package at.jku.dke.bigkgolap.engine;

import at.jku.dke.bigkgolap.model.GraphRepresentation;

/**
 * ServiceLoader-discovered factory for representation-specific {@link GraphBuilder}s.
 *
 * <p>uses this SPI to keep the Spark-backed {@code GraphFrameBuilder} out of the default classpath
 * — modules that want it depend on {@code :graph-builders-spark} at runtime, which ships a {@code
 * META-INF/services/...GraphBuilderProvider} registration.
 *
 * <p>{@code :graph-builders} does NOT register providers for RDF or LPG; those are hard-wired into
 * {@code GraphBuilders.forRepresentation()} so the always-on path doesn't depend on service
 * discovery.
 */
public interface GraphBuilderProvider {
  GraphRepresentation representation();

  GraphBuilder create();
}
