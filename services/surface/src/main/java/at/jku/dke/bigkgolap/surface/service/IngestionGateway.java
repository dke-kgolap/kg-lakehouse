package at.jku.dke.bigkgolap.surface.service;

import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.index.IndexRepository;
import at.jku.dke.bigkgolap.messaging.IngestionTask;
import at.jku.dke.bigkgolap.messaging.MessagingService;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.storage.StorageService;
import at.jku.dke.bigkgolap.surface.api.Exceptions.NotFoundException;
import at.jku.dke.bigkgolap.surface.api.Exceptions.UnsupportedMediaTypeException;
import at.jku.dke.bigkgolap.surface.api.dto.IngestionResponse;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestionGateway {

  private static final Logger log = LoggerFactory.getLogger(IngestionGateway.class);

  private final SchemaRepository schemas;
  private final StorageService storage;
  private final IndexRepository index;
  private final MessagingService messaging;
  private final List<Engine> engines;
  private final Tracer tracer;

  public IngestionGateway(
      SchemaRepository schemas,
      StorageService storage,
      IndexRepository index,
      MessagingService messaging,
      List<Engine> engines,
      Tracer tracer) {
    this.schemas = schemas;
    this.storage = storage;
    this.index = index;
    this.messaging = messaging;
    this.engines = engines;
    this.tracer = tracer;
  }

  public IngestionResponse ingest(String schemaId, MultipartFile file) {
    if (schemas.get(schemaId) == null) {
      throw new NotFoundException("Unknown schema '" + schemaId + "'");
    }

    String contentType = file.getContentType();
    if (contentType == null || contentType.isBlank()) {
      throw new UnsupportedMediaTypeException("Missing Content-Type on upload part");
    }

    Engine engine =
        engines.stream()
            .filter(
                e ->
                    e.supportedMediaTypes().stream().anyMatch(t -> t.equalsIgnoreCase(contentType)))
            .findFirst()
            .orElseThrow(
                () ->
                    new UnsupportedMediaTypeException(
                        "No engine registered for Content-Type '"
                            + contentType
                            + "'. Available: "
                            + engines.stream()
                                .flatMap(e -> e.supportedMediaTypes().stream())
                                .toList()));

    String originalName = sanitizeName(file.getOriginalFilename());
    String storedName = UUID.randomUUID() + "_" + originalName;
    long sizeBytes = file.getSize();

    try (var input = file.getInputStream()) {
      storage.store(schemaId, storedName, input, sizeBytes);
    } catch (Exception e) {
      throw new RuntimeException("Failed to store file", e);
    }

    index.upsertFileDetails(schemaId, storedName, engine.id(), originalName, sizeBytes);
    messaging.publishIngestionTask(
        new IngestionTask(
            schemaId, storedName, engine.id(), originalName, sizeBytes, Instant.now()));

    log.info(
        "Accepted ingestion: schema={} engine={} stored={} size={}",
        schemaId,
        engine.id(),
        storedName,
        sizeBytes);

    var span = tracer.currentSpan();
    String traceId = span != null ? span.context().traceId() : null;

    return new IngestionResponse(
        schemaId, storedName, storedName, engine.id(), originalName, sizeBytes, traceId);
  }

  private String sanitizeName(String raw) {
    String name = (raw != null && !raw.isBlank()) ? raw : "upload";
    return name.replace('/', '_').replace('\\', '_');
  }
}
