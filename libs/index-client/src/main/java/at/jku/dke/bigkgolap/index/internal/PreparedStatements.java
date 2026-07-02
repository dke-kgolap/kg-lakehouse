package at.jku.dke.bigkgolap.index.internal;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

public final class PreparedStatements {

  public final PreparedStatement insertHierarchy;
  public final PreparedStatement updateHierarchyContextIds;
  public final PreparedStatement insertContext;
  public final PreparedStatement insertFile;
  public final PreparedStatement insertFileDetails;
  public final PreparedStatement selectHierarchiesByPartition;
  public final PreparedStatement selectContextById;
  public final PreparedStatement selectFilesByContext;
  public final PreparedStatement selectFileDetailsBySchema;
  public final PreparedStatement countHierarchiesBySchema;
  public final PreparedStatement countContextsBySchema;
  public final PreparedStatement insertIngestionLog;
  public final PreparedStatement insertQueryLog;
  public final PreparedStatement selectIngestionLogsAll;
  public final PreparedStatement selectIngestionLogsBySchema;
  public final PreparedStatement selectQueryLogsAll;
  public final PreparedStatement selectQueryLogsBySchema;

  private PreparedStatements(
      PreparedStatement insertHierarchy,
      PreparedStatement updateHierarchyContextIds,
      PreparedStatement insertContext,
      PreparedStatement insertFile,
      PreparedStatement insertFileDetails,
      PreparedStatement selectHierarchiesByPartition,
      PreparedStatement selectContextById,
      PreparedStatement selectFilesByContext,
      PreparedStatement selectFileDetailsBySchema,
      PreparedStatement countHierarchiesBySchema,
      PreparedStatement countContextsBySchema,
      PreparedStatement insertIngestionLog,
      PreparedStatement insertQueryLog,
      PreparedStatement selectIngestionLogsAll,
      PreparedStatement selectIngestionLogsBySchema,
      PreparedStatement selectQueryLogsAll,
      PreparedStatement selectQueryLogsBySchema) {
    this.insertHierarchy = insertHierarchy;
    this.updateHierarchyContextIds = updateHierarchyContextIds;
    this.insertContext = insertContext;
    this.insertFile = insertFile;
    this.insertFileDetails = insertFileDetails;
    this.selectHierarchiesByPartition = selectHierarchiesByPartition;
    this.selectContextById = selectContextById;
    this.selectFilesByContext = selectFilesByContext;
    this.selectFileDetailsBySchema = selectFileDetailsBySchema;
    this.countHierarchiesBySchema = countHierarchiesBySchema;
    this.countContextsBySchema = countContextsBySchema;
    this.insertIngestionLog = insertIngestionLog;
    this.insertQueryLog = insertQueryLog;
    this.selectIngestionLogsAll = selectIngestionLogsAll;
    this.selectIngestionLogsBySchema = selectIngestionLogsBySchema;
    this.selectQueryLogsAll = selectQueryLogsAll;
    this.selectQueryLogsBySchema = selectQueryLogsBySchema;
  }

  public static PreparedStatements prepare(CqlSession session, String keyspace) {
    return new PreparedStatements(
        session.prepare(
            "INSERT INTO "
                + keyspace
                + ".hierarchies"
                + " (schema_id, dimension, hierarchy_hash, members, context_ids)"
                + " VALUES (?, ?, ?, ?, ?) IF NOT EXISTS"),
        session.prepare(
            "UPDATE "
                + keyspace
                + ".hierarchies"
                + " SET context_ids = context_ids + ?"
                + " WHERE schema_id = ? AND dimension = ? AND hierarchy_hash = ?"),
        session.prepare(
            "INSERT INTO "
                + keyspace
                + ".contexts"
                + " (schema_id, context_hash, hierarchy_map) VALUES (?, ?, ?)"),
        session.prepare(
            "INSERT INTO "
                + keyspace
                + ".files"
                + " (schema_id, context_hash, stored_name, engine_id) VALUES (?, ?, ?, ?)"),
        session.prepare(
            "INSERT INTO "
                + keyspace
                + ".file_details"
                + " (stored_name, schema_id, engine_id, original_name, size_bytes, ingested_at)"
                + " VALUES (?, ?, ?, ?, ?, ?)"),
        session.prepare(
            "SELECT hierarchy_hash, members, context_ids FROM "
                + keyspace
                + ".hierarchies"
                + " WHERE schema_id = ? AND dimension = ?"),
        session.prepare(
            "SELECT hierarchy_map FROM "
                + keyspace
                + ".contexts"
                + " WHERE schema_id = ? AND context_hash = ?"),
        session.prepare(
            "SELECT stored_name, engine_id FROM "
                + keyspace
                + ".files"
                + " WHERE schema_id = ? AND context_hash = ?"),
        session.prepare(
            "SELECT stored_name, size_bytes FROM "
                + keyspace
                + ".file_details"
                + " WHERE schema_id = ? ALLOW FILTERING"),
        session.prepare(
            "SELECT COUNT(*) AS cnt FROM "
                + keyspace
                + ".hierarchies"
                + " WHERE schema_id = ? ALLOW FILTERING"),
        session.prepare(
            "SELECT COUNT(*) AS cnt FROM " + keyspace + ".contexts" + " WHERE schema_id = ?"),
        session.prepare(
            "INSERT INTO "
                + keyspace
                + ".ingestion_log"
                + " (id, schema_id, stored_name, engine_id, analysis_ms, index_write_ms, total_ms,"
                + " contexts_count, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"),
        session.prepare(
            "INSERT INTO "
                + keyspace
                + ".query_log"
                + " (id, schema_id, query_text, context_resolve_ms, graph_construct_ms, merge_ms,"
                + " total_ms, contexts_count, quads_count, cache_hits, cache_misses, success,"
                + " error_message, completed_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
        session.prepare("SELECT * FROM " + keyspace + ".ingestion_log LIMIT ?"),
        session.prepare(
            "SELECT * FROM "
                + keyspace
                + ".ingestion_log"
                + " WHERE schema_id = ? LIMIT ? ALLOW FILTERING"),
        session.prepare("SELECT * FROM " + keyspace + ".query_log LIMIT ?"),
        session.prepare(
            "SELECT * FROM "
                + keyspace
                + ".query_log"
                + " WHERE schema_id = ? LIMIT ? ALLOW FILTERING"));
  }
}
