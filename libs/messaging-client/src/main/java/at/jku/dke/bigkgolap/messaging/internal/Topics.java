package at.jku.dke.bigkgolap.messaging.internal;

public final class Topics {

  public static final String INGESTION_TASKS = "lakehouse.ingestion.tasks";
  public static final String INGESTION_COMPLETED = "lakehouse.ingestion.completed";
  public static final String CACHE_INVALIDATION = "lakehouse.cache.invalidation";

  private Topics() {}
}
