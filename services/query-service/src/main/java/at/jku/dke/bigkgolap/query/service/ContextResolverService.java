package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.index.LakehouseFile;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ContextResolverService {

  private final IndexRepository index;

  public ContextResolverService(IndexRepository index) {
    this.index = index;
  }

  public Set<Context> resolveSpecific(CubeSchema schema, SliceDiceContext sliceDice) {
    return index.getSpecificContexts(schema, sliceDice);
  }

  public Set<Context> resolveCovering(CubeSchema schema, SliceDiceContext sliceDice) {
    return index.getCoveringContexts(schema, sliceDice);
  }

  /**
   * The engine id of a context's files (needed to assemble the inference TBox), or {@code null} if
   * the context has no files. Uses the first file's engine — contexts are single-engine in
   * practice.
   */
  public String engineForContext(String schemaId, String contextId) {
    return index.getFilesForContext(schemaId, contextId).stream()
        .map(LakehouseFile::engineId)
        .findFirst()
        .orElse(null);
  }
}
