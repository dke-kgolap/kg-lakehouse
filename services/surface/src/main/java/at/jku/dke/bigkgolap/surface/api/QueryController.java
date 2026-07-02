package at.jku.dke.bigkgolap.surface.api;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.observability.LakehouseTags;
import at.jku.dke.bigkgolap.observability.MeterNames;
import at.jku.dke.bigkgolap.surface.api.dto.StructuredQueryRequest;
import at.jku.dke.bigkgolap.surface.service.QueryServiceClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/schemas/{schemaId}")
public class QueryController {

  private static final String NDJSON = "application/x-ndjson";

  private final QueryServiceClient client;
  private final MeterRegistry registry;

  public QueryController(QueryServiceClient client, MeterRegistry registry) {
    this.client = client;
    this.registry = registry;
  }

  @PostMapping(value = "/query", consumes = MediaType.TEXT_PLAIN_VALUE, produces = NDJSON)
  public ResponseEntity<StreamingResponseBody> query(
      @PathVariable String schemaId,
      @RequestBody String body,
      @RequestParam(defaultValue = "RDF") GraphRepresentation representation,
      @RequestParam(defaultValue = "false") boolean reasoning) {
    return recordCounter(
        schemaId,
        () ->
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(NDJSON))
                .body(out -> client.queryToStream(schemaId, body, representation, reasoning, out)));
  }

  @PostMapping(
      value = "/query/structured",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = NDJSON)
  public ResponseEntity<StreamingResponseBody> queryStructured(
      @PathVariable String schemaId, @RequestBody StructuredQueryRequest body) {
    return recordCounter(
        schemaId,
        () ->
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(NDJSON))
                .body(out -> client.queryStructuredToStream(schemaId, body, out)));
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private <T> T recordCounter(String schemaId, ThrowingSupplier<T> block) {
    String status = LakehouseTags.STATUS_SUCCESS;
    try {
      return block.get();
    } catch (HttpClientErrorException e) {
      status = LakehouseTags.STATUS_CLIENT_ERROR;
      throw e;
    } catch (HttpServerErrorException e) {
      status = LakehouseTags.STATUS_SERVER_ERROR;
      throw e;
    } catch (RuntimeException e) {
      status = LakehouseTags.STATUS_CLIENT_ERROR;
      throw e;
    } catch (Exception e) {
      status = LakehouseTags.STATUS_CLIENT_ERROR;
      throw new RuntimeException(e);
    } finally {
      registry
          .counter(
              MeterNames.QUERY_TOTAL, LakehouseTags.SCHEMA, schemaId, LakehouseTags.STATUS, status)
          .increment();
    }
  }
}
