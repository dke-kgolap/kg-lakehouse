-- KG Lakehouse Cassandra schema.
-- The literal "lakehouse" keyspace name below is replaced at runtime by
-- CassandraSchemaInitializer with the configured keyspace.

CREATE KEYSPACE IF NOT EXISTS lakehouse
    WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS lakehouse.hierarchies (
    schema_id       TEXT,
    dimension       TEXT,
    hierarchy_hash  TEXT,
    members         MAP<TEXT, TEXT>,
    context_ids     SET<TEXT>,
    PRIMARY KEY ((schema_id, dimension), hierarchy_hash)
);

CREATE TABLE IF NOT EXISTS lakehouse.contexts (
    schema_id       TEXT,
    context_hash    TEXT,
    hierarchy_map   MAP<TEXT, TEXT>,
    PRIMARY KEY ((schema_id), context_hash)
);

CREATE TABLE IF NOT EXISTS lakehouse.files (
    schema_id       TEXT,
    context_hash    TEXT,
    stored_name     TEXT,
    engine_id       TEXT,
    PRIMARY KEY ((schema_id, context_hash), stored_name)
) WITH CLUSTERING ORDER BY (stored_name DESC);

CREATE TABLE IF NOT EXISTS lakehouse.file_details (
    stored_name     TEXT,
    schema_id       TEXT,
    engine_id       TEXT,
    original_name   TEXT,
    size_bytes      BIGINT,
    ingested_at     TIMESTAMP,
    PRIMARY KEY (stored_name)
);

CREATE TABLE IF NOT EXISTS lakehouse.ingestion_log (
    id               UUID,
    schema_id        TEXT,
    stored_name      TEXT,
    engine_id        TEXT,
    analysis_ms      BIGINT,
    index_write_ms   BIGINT,
    total_ms         BIGINT,
    contexts_count   INT,
    completed_at     TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS lakehouse.query_log (
    id                   UUID,
    schema_id            TEXT,
    query_text           TEXT,
    context_resolve_ms   BIGINT,
    graph_construct_ms   BIGINT,
    merge_ms             BIGINT,
    total_ms             BIGINT,
    contexts_count       INT,
    quads_count          BIGINT,
    cache_hits           INT,
    cache_misses         INT,
    success              BOOLEAN,
    error_message        TEXT,
    completed_at         TIMESTAMP,
    PRIMARY KEY (id)
);
