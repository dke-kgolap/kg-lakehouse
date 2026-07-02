package at.jku.dke.bigkgolap.messaging;

import java.util.function.Consumer;

public interface MessagingService extends AutoCloseable {

  void publishIngestionTask(IngestionTask task);

  void publishIngestionCompleted(IngestionCompletedEvent event);

  void publishCacheInvalidation(CacheInvalidationEvent event);

  void consumeIngestionTasks(String groupId, Consumer<IngestionTask> handler);

  void consumeCacheInvalidations(String groupId, Consumer<CacheInvalidationEvent> handler);

  @Override
  void close();
}
