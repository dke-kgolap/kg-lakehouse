package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.model.Context;
import java.util.Map;
import java.util.Set;

public record MergeAndPropagateResult(
    Map<String, Set<Context>> contextMap, Set<Context> finalContexts) {}
