package at.jku.dke.bigkgolap.ingestion.service;

import at.jku.dke.bigkgolap.engine.AnalyzerResult;
import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.index.IngestionLogEntry;
import at.jku.dke.bigkgolap.messaging.CacheInvalidationEvent;
import at.jku.dke.bigkgolap.messaging.IngestionCompletedEvent;
import at.jku.dke.bigkgolap.messaging.IngestionTask;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.observability.LakehouseTags;
import at.jku.dke.bigkgolap.observability.MeterNames;
import at.jku.dke.bigkgolap.storage.StorageService;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Per-task processor wired into {@link MessagingService#consumeIngestionTasks} by {@link
 * IngestionConsumerLifecycle}. Single-threaded processing is safe because the Kafka consumer thread
 * already serialises tasks per partition (and partitions are keyed by {@code schemaId}, so
 * per-schema ordering is preserved).
 *
 * <p>Idempotency: re-delivery on Kafka failure replays the whole pipeline. {@code upsertContext} /
 * {@code upsertFile} are idempotent; the {@code logIngestion} row gets a fresh UUID so duplicates
 * are possible. Acceptable for now.
 */
@Service
public class IngestionPipeline {

  private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

  private final SchemaRepository schemas;
  private final StorageService storage;
  private final IndexRepository index;
  private final MessagingService messaging;
  private final List<Engine> engines;
  private final ContextResolver resolver;
  private final MeterRegistry meterRegistry;
  private final DistributionSummary fileSizeSummary;

  public IngestionPipeline(
      SchemaRepository schemas,
      StorageService storage,
      IndexRepository index,
      MessagingService messaging,
      List<Engine> engines,
      ContextResolver resolver,
      MeterRegistry meterRegistry) {
    this.schemas = schemas;
    this.storage = storage;
    this.index = index;
    this.messaging = messaging;
    this.engines = engines;
    this.resolver = resolver;
    this.meterRegistry = meterRegistry;
    this.fileSizeSummary =
        DistributionSummary.builder(MeterNames.INGESTION_FILE_SIZE_BYTES)
            .baseUnit("bytes")
            .register(meterRegistry);
  }

  public void process(IngestionTask task) {
    long started = System.nanoTime();
    CubeSchema schema = lookupSchema(task);
    Engine engine = lookupEngine(task);

    Timer analysisTimer =
        Timer.builder(MeterNames.INGESTION_ANALYSIS_DURATION)
            .tags(
                LakehouseTags.SCHEMA, task.schemaId(),
                LakehouseTags.ENGINE, task.engineId())
            .register(meterRegistry);
    long analysisStart = System.nanoTime();
    AnalyzerResult analyzerResult;
    try (var input = storage.load(task.schemaId(), task.storedName())) {
      analyzerResult = engine.analyzer().analyze(input, schema);
    } catch (java.io.IOException e) {
      throw new IngestionException("Failed to load/analyze '%s'".formatted(task.storedName()), e);
    }
    long analysisNanos = System.nanoTime() - analysisStart;
    analysisTimer.record(analysisNanos, TimeUnit.NANOSECONDS);

    if (task.sizeBytes() > 0) {
      fileSizeSummary.record(task.sizeBytes());
    }

    Set<Context> contexts = resolver.resolve(analyzerResult, schema);

    Timer indexTimer =
        Timer.builder(MeterNames.INGESTION_INDEX_WRITE_DURATION)
            .tags(LakehouseTags.SCHEMA, task.schemaId())
            .register(meterRegistry);
    long indexStart = System.nanoTime();
    writeIndex(task, schema, contexts);
    long indexNanos = System.nanoTime() - indexStart;
    indexTimer.record(indexNanos, TimeUnit.NANOSECONDS);

    long totalMs = nanosToMs(System.nanoTime() - started);
    recordCompletion(task, contexts, nanosToMs(analysisNanos), nanosToMs(indexNanos), totalMs);
  }

  private CubeSchema lookupSchema(IngestionTask task) {
    CubeSchema schema = schemas.get(task.schemaId());
    if (schema == null) {
      throw new IngestionException(
          "Unknown schema '%s' for task %s".formatted(task.schemaId(), task.storedName()));
    }
    return schema;
  }

  private Engine lookupEngine(IngestionTask task) {
    for (Engine e : engines) {
      if (e.id().equals(task.engineId())) {
        return e;
      }
    }
    throw new IngestionException(
        "No engine '%s' available for task %s".formatted(task.engineId(), task.storedName()));
  }

  private void writeIndex(IngestionTask task, CubeSchema schema, Set<Context> contexts) {
    for (Context ctx : contexts) {
      index.upsertContext(schema, ctx);
      index.upsertFile(task.schemaId(), ctx.id(), task.storedName(), task.engineId());
    }
  }

  private void recordCompletion(
      IngestionTask task, Set<Context> contexts, long analysisMs, long indexMs, long totalMs) {
    index.logIngestion(
        new IngestionLogEntry(
            UUID.randomUUID(),
            task.schemaId(),
            task.storedName(),
            task.engineId(),
            analysisMs,
            indexMs,
            totalMs,
            contexts.size(),
            Instant.now()));
    messaging.publishIngestionCompleted(
        new IngestionCompletedEvent(
            task.schemaId(),
            task.storedName(),
            task.engineId(),
            contexts.size(),
            totalMs,
            Instant.now()));
    if (!contexts.isEmpty()) {
      List<String> contextIds = contexts.stream().map(Context::id).toList();
      messaging.publishCacheInvalidation(CacheInvalidationEvent.of(task.schemaId(), contextIds));
    }
    meterRegistry
        .counter(
            MeterNames.INGESTION_FILES_TOTAL,
            LakehouseTags.SCHEMA,
            task.schemaId(),
            LakehouseTags.ENGINE,
            task.engineId())
        .increment();
    log.info(
        "Ingested schema={} stored={} contexts={} totalMs={}",
        task.schemaId(),
        task.storedName(),
        contexts.size(),
        totalMs);
  }

  private static long nanosToMs(long nanos) {
    return TimeUnit.NANOSECONDS.toMillis(nanos);
  }
}
