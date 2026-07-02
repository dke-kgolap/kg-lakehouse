package at.jku.dke.bigkgolap.observability.log;

import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.index.QueryLogEntry;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronous wrapper over {@link IndexRepository#logQuery}. Failures are logged at warn and
 * swallowed — best-effort persistence; if Cassandra is slow, the per-statement timeout configured
 * in {@code CassandraIndexRepository.logQuery} (500 ms) trips and we fall through.
 *
 * <p>The trace id is captured from the active Micrometer/OpenTelemetry span so a query log row can
 * be cross-referenced with its Tempo trace by id alone.
 */
public class QueryLogger {

  private static final Logger log = LoggerFactory.getLogger(QueryLogger.class);

  private final IndexRepository index;
  private final Tracer tracer;

  public QueryLogger(IndexRepository index, Tracer tracer) {
    this.index = index;
    this.tracer = tracer;
  }

  public void log(
      String schemaId,
      String queryText,
      long contextResolveMs,
      long graphConstructMs,
      long mergeMs,
      long totalMs,
      int contextsCount,
      long quadsCount,
      int cacheHits,
      int cacheMisses,
      boolean success,
      String errorMessage) {
    try {
      index.logQuery(
          new QueryLogEntry(
              UUID.randomUUID(),
              schemaId,
              queryText,
              contextResolveMs,
              graphConstructMs,
              mergeMs,
              totalMs,
              contextsCount,
              quadsCount,
              cacheHits,
              cacheMisses,
              success,
              errorMessage,
              Instant.now()));
    } catch (RuntimeException e) {
      // Persistence is best-effort; never fail a query because the log write failed.
      log.warn(
          "logQuery failed schema={} traceId={}: {}", schemaId, currentTraceId(), e.getMessage());
    }
  }

  public String currentTraceId() {
    var span = tracer.currentSpan();
    if (span == null) return null;
    var ctx = span.context();
    return ctx != null ? ctx.traceId() : null;
  }
}
