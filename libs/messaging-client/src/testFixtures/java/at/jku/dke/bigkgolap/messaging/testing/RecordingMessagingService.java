package at.jku.dke.bigkgolap.messaging.testing;

import at.jku.dke.bigkgolap.messaging.CacheInvalidationEvent;
import at.jku.dke.bigkgolap.messaging.IngestionCompletedEvent;
import at.jku.dke.bigkgolap.messaging.IngestionTask;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Test double for {@link MessagingService} used by graph-service. Captures published events and
 * exposes a way to drive the cache-invalidation handler synchronously from tests.
 */
public class RecordingMessagingService implements MessagingService {

  public final List<IngestionTask> publishedTasks = new CopyOnWriteArrayList<>();
  public final List<IngestionCompletedEvent> completed = new CopyOnWriteArrayList<>();
  public final List<CacheInvalidationEvent> invalidations = new CopyOnWriteArrayList<>();
  public final List<String> invalidationGroupIds = new CopyOnWriteArrayList<>();

  private Consumer<CacheInvalidationEvent> invalidationHandler;

  @Override
  public void publishIngestionTask(IngestionTask task) {
    publishedTasks.add(task);
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
    // not used in graph-service tests
  }

  @Override
  public void consumeCacheInvalidations(String groupId, Consumer<CacheInvalidationEvent> handler) {
    invalidationGroupIds.add(groupId);
    invalidationHandler = handler;
  }

  public void deliverInvalidation(CacheInvalidationEvent event) {
    if (invalidationHandler == null) {
      throw new IllegalStateException("No invalidation handler registered yet");
    }
    invalidationHandler.accept(event);
  }

  @Override
  public void close() {
    // no-op
  }
}
