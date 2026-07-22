package at.jku.dke.bigkgolap.index;

import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IndexRepository {
  void upsertHierarchy(
      String schemaId,
      String dimension,
      String hierarchyHash,
      Map<String, String> members,
      String contextId);

  void upsertContext(CubeSchema schema, Context context);

  void upsertFile(String schemaId, String contextHash, String storedName, String engineId);

  void upsertFileDetails(
      String schemaId, String storedName, String engineId, String originalName, long sizeBytes);

  Set<Context> getSpecificContexts(CubeSchema schema, SliceDiceContext sliceDice);

  /**
   * Stored contexts whose knowledge propagates into the scope without being part of it: comparable
   * with the scope coordinate in every dimension (an unconstrained dimension reads as the all
   * level) and strictly coarser in at least one. This covers both contexts above the scope on every
   * dimension (the classic general contexts) and mixed-granularity contexts that are finer than the
   * scope in one dimension and coarser in another (KG-OLAP Definition 4); which cells each one
   * covers is decided per context by the caller.
   */
  Set<Context> getCoveringContexts(CubeSchema schema, SliceDiceContext sliceDice);

  List<LakehouseFile> getFilesForContext(String schemaId, String contextHash);

  LakehouseStats getStats(String schemaId);

  void logIngestion(IngestionLogEntry entry);

  void logQuery(QueryLogEntry entry);

  List<IngestionLogEntry> getIngestionLogs(String schemaId, int limit);

  List<QueryLogEntry> getQueryLogs(String schemaId, int limit);

  void reset();
}
