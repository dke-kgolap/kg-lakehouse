package at.jku.dke.bigkgolap.surface.api.dto;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import java.util.Map;

/** Wire payload sent to query-service's {@code /query/structured} endpoint. */
public record StructuredWireRequest(
    String schemaId,
    Map<String, Map<String, String>> select,
    Map<String, String> rollup,
    GraphRepresentation representation,
    String format,
    boolean reasoning) {}
