package at.jku.dke.bigkgolap.ingestion.service;

import at.jku.dke.bigkgolap.ingestion.config.LakehouseProperties;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Subscribes the {@link IngestionPipeline} to the ingestion-tasks topic at application start.
 *
 * <p>The consumer thread is owned by {@link MessagingService}; it is shut down through {@link
 * MessagingService#close()}, which is wired via the bean's {@code destroyMethod} in {@link
 * at.jku.dke.bigkgolap.ingestion.config.MessagingConfig}.
 */
@Component
@Profile("!test")
public class IngestionConsumerLifecycle {

  private static final Logger log = LoggerFactory.getLogger(IngestionConsumerLifecycle.class);

  private final MessagingService messaging;
  private final IngestionPipeline pipeline;
  private final LakehouseProperties props;

  public IngestionConsumerLifecycle(
      MessagingService messaging, IngestionPipeline pipeline, LakehouseProperties props) {
    this.messaging = messaging;
    this.pipeline = pipeline;
    this.props = props;
  }

  @PostConstruct
  public void start() {
    String groupId = props.messaging().consumerGroup();
    log.info("Starting ingestion consumer (groupId={})", groupId);
    messaging.consumeIngestionTasks(groupId, task -> pipeline.process(task));
  }
}
