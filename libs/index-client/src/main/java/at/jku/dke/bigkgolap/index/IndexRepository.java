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

  Set<String> getGeneralContextIds(CubeSchema schema, SliceDiceContext sliceDice);

  List<LakehouseFile> getFilesForContext(String schemaId, String contextHash);

  LakehouseStats getStats(String schemaId);

  void logIngestion(IngestionLogEntry entry);

  void logQuery(QueryLogEntry entry);

  List<IngestionLogEntry> getIngestionLogs(String schemaId, int limit);

  List<QueryLogEntry> getQueryLogs(String schemaId, int limit);

  void reset();
}
