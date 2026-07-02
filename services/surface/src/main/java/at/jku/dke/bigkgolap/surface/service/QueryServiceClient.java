package at.jku.dke.bigkgolap.surface.service;

import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.surface.api.Exceptions.BadRequestException;
import at.jku.dke.bigkgolap.surface.api.Exceptions.NotFoundException;
import at.jku.dke.bigkgolap.surface.api.dto.ClearCacheResponse;
import at.jku.dke.bigkgolap.surface.api.dto.QueryRequest;
import at.jku.dke.bigkgolap.surface.api.dto.StructuredQueryRequest;
import at.jku.dke.bigkgolap.surface.api.dto.StructuredWireRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class QueryServiceClient {

  private static final int NOT_FOUND = 404;

  private final RestClient queryServiceRestClient;

  public QueryServiceClient(RestClient queryServiceRestClient) {
    this.queryServiceRestClient = queryServiceRestClient;
  }

  public void queryToStream(
      String schemaId,
      String queryText,
      GraphRepresentation representation,
      boolean reasoning,
      OutputStream out) {
    queryServiceRestClient
        .post()
        .uri("/query")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new QueryRequest(schemaId, queryText, representation, "application/n-quads", reasoning))
        .exchange(
            (req, res) -> {
              if (res.getStatusCode().isError()) {
                translateStream(
                    res.getStatusCode(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
              }
              res.getBody().transferTo(out);
              return null;
            });
  }

  public void queryStructuredToStream(
      String schemaId, StructuredQueryRequest body, OutputStream out) {
    queryServiceRestClient
        .post()
        .uri("/query/structured")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new StructuredWireRequest(
                schemaId,
                body.select(),
                body.rollup(),
                body.representation(),
                body.format(),
                body.reasoning()))
        .exchange(
            (req, res) -> {
              if (res.getStatusCode().isError()) {
                translateStream(
                    res.getStatusCode(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
              }
              res.getBody().transferTo(out);
              return null;
            });
  }

  public ClearCacheResponse clearAllCaches() {
    var result =
        queryServiceRestClient
            .post()
            .uri("/admin/cache/clear")
            .retrieve()
            .onStatus(HttpStatusCode::isError, this::translate)
            .body(ClearCacheResponse.class);
    if (result == null) {
      throw new IllegalStateException("Empty response from query-service");
    }
    return result;
  }

  private void translateStream(HttpStatusCode status, String body) {
    if (status.value() == NOT_FOUND) {
      throw new NotFoundException(body.isBlank() ? "query-service: 404" : body);
    } else if (status.is4xxClientError()) {
      throw new BadRequestException(body.isBlank() ? "query-service: " + status.value() : body);
    } else {
      throw new IllegalStateException("query-service responded " + status.value() + ": " + body);
    }
  }

  private void translate(
      org.springframework.http.HttpRequest request,
      org.springframework.http.client.ClientHttpResponse response)
      throws IOException {
    var status = response.getStatusCode();
    String body;
    try {
      body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      body = "";
    }
    if (status.value() == NOT_FOUND) {
      throw new NotFoundException(body.isBlank() ? "query-service: 404" : body);
    } else if (status.is4xxClientError()) {
      throw new BadRequestException(body.isBlank() ? "query-service: " + status.value() : body);
    } else {
      throw new IllegalStateException("query-service responded " + status.value() + ": " + body);
    }
  }
}
