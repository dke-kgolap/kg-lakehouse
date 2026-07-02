package at.jku.dke.bigkgolap.index.testing;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.index.IngestionLogEntry;
import at.jku.dke.bigkgolap.index.LakehouseFile;
import at.jku.dke.bigkgolap.index.LakehouseStats;
import at.jku.dke.bigkgolap.index.QueryLogEntry;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryIndexRepository implements IndexRepository {

  public record HierarchyKey(String schemaId, String dimension, String hash) {}

  public record FileKey(String schemaId, String storedName) {}

  public record FileDetail(String engineId, String originalName, long sizeBytes) {}

  public final Map<HierarchyKey, Set<String>> hierarchies = new ConcurrentHashMap<>();
  public final Map<Map.Entry<String, String>, Map<String, String>> contexts =
      new ConcurrentHashMap<>();
  public final List<Map.Entry<String, Map.Entry<String, String>>> files =
      new CopyOnWriteArrayList<>();
  public final Map<FileKey, FileDetail> fileDetails = new ConcurrentHashMap<>();
  public final List<IngestionLogEntry> ingestionLog = new CopyOnWriteArrayList<>();
  public final List<QueryLogEntry> queryLog = new CopyOnWriteArrayList<>();

  private final Map<Map.Entry<String, String>, Context> storedContexts = new ConcurrentHashMap<>();

  @Override
  public void upsertHierarchy(
      String schemaId,
      String dimension,
      String hierarchyHash,
      Map<String, String> members,
      String contextId) {
    hierarchies
        .computeIfAbsent(
            new HierarchyKey(schemaId, dimension, hierarchyHash),
            k -> Collections.synchronizedSet(new HashSet<>()))
        .add(contextId);
  }

  @Override
  public void upsertContext(CubeSchema schema, Context context) {
    var key = entry(schema.id(), context.id());
    var hierarchyMap = new java.util.HashMap<String, String>();
    for (var e : context.hierarchies().entrySet()) {
      hierarchyMap.put(e.getKey(), e.getValue().id());
    }
    contexts.put(key, hierarchyMap);
    storedContexts.put(key, context);
    for (var e : context.hierarchies().entrySet()) {
      hierarchies
          .computeIfAbsent(
              new HierarchyKey(schema.id(), e.getKey(), e.getValue().id()),
              k -> Collections.synchronizedSet(new HashSet<>()))
          .add(context.id());
    }
  }

  @Override
  public void upsertFile(String schemaId, String contextHash, String storedName, String engineId) {
    files.add(entry(schemaId, entry(contextHash, storedName)));
    fileDetails.putIfAbsent(
        new FileKey(schemaId, storedName), new FileDetail(engineId, storedName, 0));
  }

  @Override
  public void upsertFileDetails(
      String schemaId, String storedName, String engineId, String originalName, long sizeBytes) {
    fileDetails.put(
        new FileKey(schemaId, storedName), new FileDetail(engineId, originalName, sizeBytes));
  }

  @Override
  public Set<Context> getSpecificContexts(CubeSchema schema, SliceDiceContext sliceDice) {
    var result = new HashSet<Context>();
    for (var e : storedContexts.entrySet()) {
      if (!e.getKey().getKey().equals(schema.id())) continue;
      if (matches(e.getValue(), sliceDice)) result.add(e.getValue());
    }
    return result;
  }

  @Override
  public Set<String> getGeneralContextIds(CubeSchema schema, SliceDiceContext sliceDice) {
    var result = new HashSet<String>();
    for (var e : storedContexts.entrySet()) {
      if (!e.getKey().getKey().equals(schema.id())) continue;
      if (generalMatches(e.getValue(), sliceDice)) result.add(e.getValue().id());
    }
    return result;
  }

  private boolean matches(Context stored, SliceDiceContext sliceDice) {
    for (var e : sliceDice.hierarchies().entrySet()) {
      String dim = e.getKey();
      Hierarchy queryHierarchy = e.getValue();
      Hierarchy storedHierarchy = stored.getHierarchy(dim);
      if (storedHierarchy == null) return false;
      if (queryHierarchy.members().size() > storedHierarchy.members().size()) return false;
      for (int i = 0; i < queryHierarchy.members().size(); i++) {
        if (!queryHierarchy.members().get(i).equals(storedHierarchy.members().get(i))) return false;
      }
    }
    return true;
  }

  private boolean generalMatches(Context stored, SliceDiceContext sliceDice) {
    if (sliceDice.isEmpty()) return false;
    for (var e : sliceDice.hierarchies().entrySet()) {
      String dim = e.getKey();
      Hierarchy queryHierarchy = e.getValue();
      Hierarchy storedHierarchy = stored.getHierarchy(dim);
      if (storedHierarchy == null) return false;
      if (storedHierarchy.members().size() >= queryHierarchy.members().size()) return false;
      for (int i = 0; i < storedHierarchy.members().size(); i++) {
        if (!storedHierarchy.members().get(i).equals(queryHierarchy.members().get(i))) return false;
      }
    }
    return true;
  }

  @Override
  public List<LakehouseFile> getFilesForContext(String schemaId, String contextHash) {
    var result = new ArrayList<LakehouseFile>();
    for (var f : files) {
      if (!f.getKey().equals(schemaId)) continue;
      if (!f.getValue().getKey().equals(contextHash)) continue;
      String storedName = f.getValue().getValue();
      FileDetail detail = fileDetails.get(new FileKey(schemaId, storedName));
      if (detail != null) result.add(new LakehouseFile(storedName, detail.engineId()));
    }
    return result;
  }

  @Override
  public LakehouseStats getStats(String schemaId) {
    long totalHierarchies =
        hierarchies.keySet().stream().filter(k -> k.schemaId().equals(schemaId)).count();
    long totalContexts =
        contexts.keySet().stream().filter(k -> k.getKey().equals(schemaId)).count();
    long totalBytes = 0L;
    long totalFiles = 0L;
    for (var e : fileDetails.entrySet()) {
      if (e.getKey().schemaId().equals(schemaId)) {
        totalFiles++;
        totalBytes += e.getValue().sizeBytes();
      }
    }
    return new LakehouseStats(schemaId, totalHierarchies, totalContexts, totalFiles, totalBytes);
  }

  @Override
  public void logIngestion(IngestionLogEntry entry) {
    ingestionLog.add(entry);
  }

  @Override
  public void logQuery(QueryLogEntry entry) {
    queryLog.add(entry);
  }

  @Override
  public List<IngestionLogEntry> getIngestionLogs(String schemaId, int limit) {
    var filtered = new ArrayList<IngestionLogEntry>();
    for (var e : ingestionLog) {
      if (schemaId == null || e.schemaId().equals(schemaId)) filtered.add(e);
    }
    int from = Math.max(0, filtered.size() - limit);
    return filtered.subList(from, filtered.size());
  }

  @Override
  public List<QueryLogEntry> getQueryLogs(String schemaId, int limit) {
    var filtered = new ArrayList<QueryLogEntry>();
    for (var e : queryLog) {
      if (schemaId == null || e.schemaId().equals(schemaId)) filtered.add(e);
    }
    int from = Math.max(0, filtered.size() - limit);
    return filtered.subList(from, filtered.size());
  }

  @Override
  public void reset() {
    hierarchies.clear();
    contexts.clear();
    storedContexts.clear();
    files.clear();
    fileDetails.clear();
    ingestionLog.clear();
    queryLog.clear();
  }

  private static <K, V> Map.Entry<K, V> entry(K key, V value) {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }
}
