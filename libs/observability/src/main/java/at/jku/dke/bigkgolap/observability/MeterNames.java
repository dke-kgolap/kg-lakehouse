package at.jku.dke.bigkgolap.observability;

import java.util.Set;

public final class MeterNames {

  public static final String INGESTION_FILES_TOTAL = "lakehouse.ingestion.files.total";
  public static final String INGESTION_ANALYSIS_DURATION = "lakehouse.ingestion.analysis.duration";
  public static final String INGESTION_INDEX_WRITE_DURATION =
      "lakehouse.ingestion.index.write.duration";
  public static final String INGESTION_FILE_SIZE_BYTES = "lakehouse.ingestion.file.size.bytes";
  public static final String INGESTION_QUEUE_DEPTH = "lakehouse.ingestion.queue.depth";

  public static final String QUERY_TOTAL = "lakehouse.query.total";
  public static final String QUERY_CONTEXT_RESOLUTION_DURATION =
      "lakehouse.query.context.resolution.duration";
  public static final String QUERY_GRAPH_CONSTRUCTION_DURATION =
      "lakehouse.query.graph.construction.duration";
  public static final String QUERY_MERGE_DURATION = "lakehouse.query.merge.duration";
  public static final String QUERY_TOTAL_DURATION = "lakehouse.query.total.duration";
  public static final String QUERY_CACHE_HIT = "lakehouse.query.cache.hit";
  public static final String QUERY_CONTEXTS_COUNT = "lakehouse.query.contexts.count";
  public static final String QUERY_QUADS_COUNT = "lakehouse.query.quads.count";

  public static final Set<String> all =
      Set.of(
          INGESTION_FILES_TOTAL,
          INGESTION_ANALYSIS_DURATION,
          INGESTION_INDEX_WRITE_DURATION,
          INGESTION_FILE_SIZE_BYTES,
          INGESTION_QUEUE_DEPTH,
          QUERY_TOTAL,
          QUERY_CONTEXT_RESOLUTION_DURATION,
          QUERY_GRAPH_CONSTRUCTION_DURATION,
          QUERY_MERGE_DURATION,
          QUERY_TOTAL_DURATION,
          QUERY_CACHE_HIT,
          QUERY_CONTEXTS_COUNT,
          QUERY_QUADS_COUNT);

  private MeterNames() {}
}
