package at.jku.dke.bigkgolap.index;

import at.jku.dke.bigkgolap.index.internal.CartesianProduct;
import at.jku.dke.bigkgolap.index.internal.HierarchyCodec;
import at.jku.dke.bigkgolap.index.internal.PreparedStatements;
import at.jku.dke.bigkgolap.index.internal.StoredHierarchy;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Dimension;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CassandraIndexRepository implements IndexRepository {

  private static final List<String> TABLES =
      List.of("hierarchies", "contexts", "files", "file_details", "ingestion_log", "query_log");

  // per-statement timeout on logQuery so a slow Cassandra write
  // can't inflate `lakehouse.query.total.duration`. Best-effort persistence.
  private static final Duration QUERY_LOG_TIMEOUT = Duration.ofMillis(500);

  private final CqlSession session;
  private final String keyspace;
  private final PreparedStatements ps;

  public CassandraIndexRepository(CqlSession session) {
    this(session, "lakehouse");
  }

  public CassandraIndexRepository(CqlSession session, String keyspace) {
    this.session = session;
    this.keyspace = keyspace;
    this.ps = PreparedStatements.prepare(session, keyspace);
  }

  // ------------------------------------------------------------------
  // Writes
  // ------------------------------------------------------------------

  @Override
  public void upsertHierarchy(
      String schemaId,
      String dimension,
      String hierarchyHash,
      Map<String, String> members,
      String contextId) {
    Set<String> contextIdSet = Set.of(contextId);
    var initial =
        quorum(ps.insertHierarchy.bind(schemaId, dimension, hierarchyHash, members, contextIdSet));
    session.execute(initial);
    var update =
        quorum(ps.updateHierarchyContextIds.bind(contextIdSet, schemaId, dimension, hierarchyHash));
    session.execute(update);
  }

  @Override
  public void upsertContext(CubeSchema schema, Context context) {
    String schemaId = schema.id();
    String contextId = context.id();
    for (var entry : context.hierarchies().entrySet()) {
      String dimension = entry.getKey();
      Hierarchy hierarchy = entry.getValue();
      upsertHierarchy(
          schemaId, dimension, hierarchy.id(), HierarchyCodec.encode(hierarchy), contextId);
    }
    var hierarchyMap = new HashMap<String, String>();
    for (var entry : context.hierarchies().entrySet()) {
      hierarchyMap.put(entry.getKey(), entry.getValue().id());
    }
    session.execute(quorum(ps.insertContext.bind(schemaId, contextId, hierarchyMap)));
  }

  @Override
  public void upsertFile(String schemaId, String contextHash, String storedName, String engineId) {
    session.execute(quorum(ps.insertFile.bind(schemaId, contextHash, storedName, engineId)));
  }

  @Override
  public void upsertFileDetails(
      String schemaId, String storedName, String engineId, String originalName, long sizeBytes) {
    session.execute(
        quorum(
            ps.insertFileDetails.bind(
                storedName, schemaId, engineId, originalName, sizeBytes, Instant.now())));
  }

  @Override
  public void logIngestion(IngestionLogEntry entry) {
    session.execute(
        quorum(
            ps.insertIngestionLog.bind(
                entry.id(),
                entry.schemaId(),
                entry.storedName(),
                entry.engineId(),
                entry.analysisMs(),
                entry.indexWriteMs(),
                entry.totalMs(),
                entry.contextsCount(),
                entry.completedAt())));
  }

  @Override
  public void logQuery(QueryLogEntry entry) {
    session.execute(
        quorum(
                ps.insertQueryLog.bind(
                    entry.id(),
                    entry.schemaId(),
                    entry.queryText(),
                    entry.contextResolveMs(),
                    entry.graphConstructMs(),
                    entry.mergeMs(),
                    entry.totalMs(),
                    entry.contextsCount(),
                    entry.quadsCount(),
                    entry.cacheHits(),
                    entry.cacheMisses(),
                    entry.success(),
                    entry.errorMessage(),
                    entry.completedAt()))
            .setTimeout(QUERY_LOG_TIMEOUT));
  }

  @Override
  public void reset() {
    for (var table : TABLES) {
      session.execute("TRUNCATE " + keyspace + "." + table);
    }
  }

  // ------------------------------------------------------------------
  // Reads
  // ------------------------------------------------------------------

  @Override
  public List<LakehouseFile> getFilesForContext(String schemaId, String contextHash) {
    var rs = session.execute(quorum(ps.selectFilesByContext.bind(schemaId, contextHash)));
    var result = new ArrayList<LakehouseFile>();
    for (var row : rs) {
      result.add(new LakehouseFile(row.getString("stored_name"), row.getString("engine_id")));
    }
    return result;
  }

  @Override
  public Set<Context> getSpecificContexts(CubeSchema schema, SliceDiceContext sliceDice) {
    var perDimension = new ArrayList<List<StoredHierarchy>>();
    for (var dim : schema.dimensions().values()) {
      Hierarchy constraint = sliceDice.getHierarchy(dim.name());
      if (constraint == null) {
        constraint = Hierarchy.all(dim.name());
      }
      var partition = readPartition(schema.id(), dim);
      var filtered = new ArrayList<StoredHierarchy>();
      for (var sh : partition) {
        if (matches(sh.hierarchy(), constraint)) {
          filtered.add(sh);
        }
      }
      perDimension.add(filtered);
    }
    var result = new HashSet<Context>();
    for (var tuple : CartesianProduct.of(perDimension)) {
      var intersected = intersectContextIds(tuple);
      if (intersected.size() == 0) {
        // no match — skip
      } else if (intersected.size() == 1) {
        var hierarchies = new ArrayList<Hierarchy>();
        for (var sh : tuple) hierarchies.add(sh.hierarchy());
        result.add(Context.of(hierarchies, schema));
      } else {
        var hierarchyList = new ArrayList<Hierarchy>();
        for (var sh : tuple) hierarchyList.add(sh.hierarchy());
        throw new IndexCorruptionException(
            "Multiple context ids ("
                + intersected
                + ") share the complete hierarchy tuple "
                + hierarchyList
                + " in schema '"
                + schema.id()
                + "'");
      }
    }
    return result;
  }

  /** A per-dimension candidate for covering resolution, tagged with its direction vs. the scope. */
  private record TaggedHierarchy(StoredHierarchy stored, boolean strictlyCoarser) {}

  @Override
  public Set<Context> getCoveringContexts(CubeSchema schema, SliceDiceContext sliceDice) {
    var perDimension = new ArrayList<List<TaggedHierarchy>>();
    for (var dim : schema.dimensions().values()) {
      Hierarchy constraint = sliceDice.getHierarchy(dim.name());
      if (constraint == null) {
        constraint = Hierarchy.all(dim.name());
      }
      var chain = generalChain(constraint, schema);
      var partition = readPartition(schema.id(), dim);
      var candidates = new ArrayList<TaggedHierarchy>();
      for (var sh : partition) {
        if (matches(sh.hierarchy(), constraint)) {
          // finer than or equal to the scope on this dimension
          candidates.add(new TaggedHierarchy(sh, false));
        } else if (onChain(sh.hierarchy(), chain)) {
          // strictly coarser: an ancestor of the scope coordinate on this dimension
          candidates.add(new TaggedHierarchy(sh, true));
        }
        // otherwise incomparable with the scope on this dimension — not a covering candidate
      }
      perDimension.add(candidates);
    }
    var result = new HashSet<Context>();
    for (var tuple : CartesianProduct.of(perDimension)) {
      boolean anyCoarser = false;
      for (var th : tuple) {
        if (th.strictlyCoarser()) {
          anyCoarser = true;
          break;
        }
      }
      // tuples with no strictly-coarser dimension are the specific contexts (or the scope itself)
      if (!anyCoarser) continue;
      var shTuple = new ArrayList<StoredHierarchy>(tuple.size());
      for (var th : tuple) shTuple.add(th.stored());
      var intersected = intersectContextIds(shTuple);
      if (intersected.size() == 0) {
        // no match — skip
      } else if (intersected.size() == 1) {
        var hierarchies = new ArrayList<Hierarchy>();
        for (var th : tuple) hierarchies.add(th.stored().hierarchy());
        result.add(Context.of(hierarchies, schema));
      } else {
        var hierarchyList = new ArrayList<Hierarchy>();
        for (var th : tuple) hierarchyList.add(th.stored().hierarchy());
        throw new IndexCorruptionException(
            "Multiple context ids ("
                + intersected
                + ") share the complete hierarchy tuple "
                + hierarchyList
                + " in schema '"
                + schema.id()
                + "'");
      }
    }
    return result;
  }

  private boolean onChain(Hierarchy stored, List<Hierarchy> chain) {
    for (var ancestor : chain) {
      if (matchesExactly(stored, ancestor)) return true;
    }
    return false;
  }

  @Override
  public LakehouseStats getStats(String schemaId) {
    long totalHierarchies = singleCount(quorum(ps.countHierarchiesBySchema.bind(schemaId)));
    long totalContexts = singleCount(quorum(ps.countContextsBySchema.bind(schemaId)));
    long totalFiles = 0L;
    long totalBytes = 0L;
    var rs = session.execute(quorum(ps.selectFileDetailsBySchema.bind(schemaId)));
    for (var row : rs) {
      totalFiles++;
      totalBytes += row.getLong("size_bytes");
    }
    return new LakehouseStats(schemaId, totalHierarchies, totalContexts, totalFiles, totalBytes);
  }

  @Override
  public List<IngestionLogEntry> getIngestionLogs(String schemaId, int limit) {
    BoundStatement bound =
        schemaId == null
            ? quorum(ps.selectIngestionLogsAll.bind(limit))
            : quorum(ps.selectIngestionLogsBySchema.bind(schemaId, limit));
    var rs = session.execute(bound);
    var result = new ArrayList<IngestionLogEntry>();
    for (var row : rs) {
      result.add(
          new IngestionLogEntry(
              row.getUuid("id"),
              row.getString("schema_id"),
              row.getString("stored_name"),
              row.getString("engine_id"),
              row.getLong("analysis_ms"),
              row.getLong("index_write_ms"),
              row.getLong("total_ms"),
              row.getInt("contexts_count"),
              row.getInstant("completed_at")));
    }
    return result;
  }

  @Override
  public List<QueryLogEntry> getQueryLogs(String schemaId, int limit) {
    BoundStatement bound =
        schemaId == null
            ? quorum(ps.selectQueryLogsAll.bind(limit))
            : quorum(ps.selectQueryLogsBySchema.bind(schemaId, limit));
    var rs = session.execute(bound);
    var result = new ArrayList<QueryLogEntry>();
    for (var row : rs) {
      String queryText = row.getString("query_text");
      result.add(
          new QueryLogEntry(
              row.getUuid("id"),
              row.getString("schema_id"),
              queryText != null ? queryText : "",
              row.getLong("context_resolve_ms"),
              row.getLong("graph_construct_ms"),
              row.getLong("merge_ms"),
              row.getLong("total_ms"),
              row.getInt("contexts_count"),
              row.getLong("quads_count"),
              row.getInt("cache_hits"),
              row.getInt("cache_misses"),
              row.getBoolean("success"),
              row.getString("error_message"),
              row.getInstant("completed_at")));
    }
    return result;
  }

  // ------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------

  private List<StoredHierarchy> readPartition(String schemaId, Dimension dimension) {
    var rs =
        session.execute(quorum(ps.selectHierarchiesByPartition.bind(schemaId, dimension.name())));
    var result = new ArrayList<StoredHierarchy>();
    for (var row : rs) {
      Map<String, String> members = row.getMap("members", String.class, String.class);
      if (members == null) members = Map.of();
      Set<String> contextIds = row.getSet("context_ids", String.class);
      if (contextIds == null) contextIds = Set.of();
      result.add(new StoredHierarchy(HierarchyCodec.decode(dimension, members), contextIds));
    }
    return result;
  }

  private boolean matches(Hierarchy stored, Hierarchy constraint) {
    if (constraint.isAllLevel()) return true;
    for (var m : constraint.members()) {
      Member storedMember = null;
      for (var sm : stored.members()) {
        if (sm.level().name().equals(m.level().name())) {
          storedMember = sm;
          break;
        }
      }
      if (storedMember == null) return false;
      if (!storedMember.value().equals(m.value())) return false;
    }
    return true;
  }

  private boolean matchesExactly(Hierarchy stored, Hierarchy ancestor) {
    if (stored.members().size() != ancestor.members().size()) return false;
    for (var am : ancestor.members()) {
      Member sm = null;
      for (var s : stored.members()) {
        if (s.level().name().equals(am.level().name())) {
          sm = s;
          break;
        }
      }
      if (sm == null) return false;
      if (!sm.value().equals(am.value())) return false;
    }
    return true;
  }

  private List<Hierarchy> generalChain(Hierarchy leaf, CubeSchema schema) {
    var chain = new ArrayList<Hierarchy>();
    Hierarchy current = leaf;
    while (true) {
      chain.add(current);
      if (current.isAllLevel()) break;
      current = current.rollUp(schema);
    }
    return chain;
  }

  private Set<String> intersectContextIds(List<StoredHierarchy> tuple) {
    if (tuple.isEmpty()) return Collections.emptySet();
    var intersect = new HashSet<>(tuple.get(0).associatedContexts());
    for (int i = 1; i < tuple.size(); i++) {
      intersect.retainAll(tuple.get(i).associatedContexts());
      if (intersect.isEmpty()) return Collections.emptySet();
    }
    return intersect;
  }

  private long singleCount(BoundStatement stmt) {
    Row row = session.execute(stmt).one();
    return row == null ? 0L : row.getLong("cnt");
  }

  private static BoundStatement quorum(BoundStatement stmt) {
    return stmt.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
  }
}
