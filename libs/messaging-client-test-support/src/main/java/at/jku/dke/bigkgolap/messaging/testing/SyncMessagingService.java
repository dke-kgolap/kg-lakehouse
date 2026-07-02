package at.jku.dke.bigkgolap.messaging.testing;

import at.jku.dke.bigkgolap.messaging.CacheInvalidationEvent;
import at.jku.dke.bigkgolap.messaging.IngestionCompletedEvent;
import at.jku.dke.bigkgolap.messaging.IngestionTask;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Test double for {@link MessagingService} that dispatches consumed tasks synchronously to whatever
 * handler is registered via {@link #consumeIngestionTasks}. {@code publishIngestionTask} records
 * the task AND immediately invokes the handler, so a single test thread drives the full pipeline
 * without spinning up Kafka.
 */
public class SyncMessagingService implements MessagingService {

  public final List<IngestionTask> publishedTasks = new CopyOnWriteArrayList<>();
  public final List<IngestionCompletedEvent> completed = new CopyOnWriteArrayList<>();
  public final List<CacheInvalidationEvent> invalidations = new CopyOnWriteArrayList<>();

  private Consumer<IngestionTask> taskHandler;

  @Override
  public void publishIngestionTask(IngestionTask task) {
    publishedTasks.add(task);
    if (taskHandler != null) {
      taskHandler.accept(task);
    }
  }

  @Override
  public void publishIngestionCompleted(IngestionCompletedEvent event) {
    completed.add(event);
  }

  @Override
  public void publishCacheInvalidation(CacheInvalidationEvent event) {
    invalidations.add(event);
  }

  @Override
  public void consumeIngestionTasks(String groupId, Consumer<IngestionTask> handler) {
    taskHandler = handler;
  }

  @Override
  public void consumeCacheInvalidations(String groupId, Consumer<CacheInvalidationEvent> handler) {
    // not used in ingestion-service tests
  }

  @Override
  public void close() {
    // no-op
  }

  public void reset() {
    publishedTasks.clear();
    completed.clear();
    invalidations.clear();
  }
}
