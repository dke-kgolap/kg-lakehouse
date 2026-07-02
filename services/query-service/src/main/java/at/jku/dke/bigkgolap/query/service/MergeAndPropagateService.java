package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.MergeLevels;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MergeAndPropagateService {

  public MergeAndPropagateResult mergeAndPropagate(
      CubeSchema schema, Set<Context> storedContexts, MergeLevels mergeLevels) {
    var contextMap = new ConcurrentHashMap<String, Set<Context>>(storedContexts.size());
    Set<Context> finalContexts = ConcurrentHashMap.newKeySet();

    storedContexts.parallelStream()
        .forEach(
            stored -> {
              var relevant = new HashSet<Context>();
              for (Context candidate : storedContexts) {
                if (candidate.id().equals(stored.id()) || candidate.rollsUpTo(stored, schema)) {
                  Context finalCtx =
                      mergeLevels == null ? candidate : candidate.rollUpTo(mergeLevels, schema);
                  relevant.add(finalCtx);
                  finalContexts.add(finalCtx);
                }
              }
              contextMap.put(stored.id(), relevant);
            });

    return new MergeAndPropagateResult(contextMap, finalContexts);
  }
}
