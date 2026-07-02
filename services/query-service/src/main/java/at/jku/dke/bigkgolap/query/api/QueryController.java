package at.jku.dke.bigkgolap.query.api;

import at.jku.dke.bigkgolap.query.api.dto.QueryRequest;
import at.jku.dke.bigkgolap.query.api.dto.QueryResponse;
import at.jku.dke.bigkgolap.query.api.dto.StructuredWireRequest;
import at.jku.dke.bigkgolap.query.service.ParsedQuery;
import at.jku.dke.bigkgolap.query.service.QueryOrchestrator;
import at.jku.dke.bigkgolap.query.service.QueryParserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class QueryController {

  public static final String NDJSON = "application/x-ndjson";

  private final QueryParserService parser;
  private final QueryOrchestrator orchestrator;
  private final SchemaResolver schemaResolver;

  public QueryController(
      QueryParserService parser, QueryOrchestrator orchestrator, SchemaResolver schemaResolver) {
    this.parser = parser;
    this.orchestrator = orchestrator;
    this.schemaResolver = schemaResolver;
  }

  @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = NDJSON)
  public ResponseEntity<StreamingResponseBody> query(@RequestBody QueryRequest req) {
    var schema = schemaResolver.requireSchema(req.schemaId());
    var parsed =
        parser
            .parseText(req.query(), schema)
            .copy(req.representation(), req.format())
            .withReasoning(req.reasoning());
    return streamOf(req.schemaId(), req.query(), parsed);
  }

  @PostMapping(
      value = "/query/structured",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = NDJSON)
  public ResponseEntity<StreamingResponseBody> queryStructured(
      @RequestBody StructuredWireRequest req) {
    var schema = schemaResolver.requireSchema(req.schemaId());
    var parsed =
        parser
            .parseStructured(req.select(), req.rollup(), req.representation(), req.format(), schema)
            .withReasoning(req.reasoning());
    String synthesizedText = "[structured] select=" + req.select() + " rollup=" + req.rollup();
    return streamOf(req.schemaId(), synthesizedText, parsed);
  }

  // streaming body must catch all — status 200 already sent, can only write error line
  private ResponseEntity<StreamingResponseBody> streamOf(
      String schemaId, String queryText, ParsedQuery parsed) {
    StreamingResponseBody body =
        out -> {
          try {
            QueryResponse meta =
                orchestrator.executeStreaming(
                    schemaId,
                    queryText,
                    parsed,
                    quad -> {
                      synchronized (out) {
                        try {
                          out.write(quad.getBytes());
                          out.write('\n');
                        } catch (java.io.IOException e) {
                          throw new RuntimeException(e);
                        }
                      }
                    });
            synchronized (out) {
              out.write(summaryLine(meta).getBytes());
              out.write('\n');
              out.flush();
            }
          } catch (Exception e) {
            synchronized (out) {
              try {
                out.write(errorLine(e).getBytes());
                out.write('\n');
                out.flush();
              } catch (java.io.IOException ignored) {
                // nothing to do — output stream is already closed
              }
            }
          }
        };
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(NDJSON)).body(body);
  }

  public static String summaryLine(QueryResponse meta) {
    var t = meta.timings();
    String traceJson = meta.traceId() != null ? "\"" + meta.traceId() + "\"" : "null";
    String timingsJson =
        "{\"contextResolutionMs\":"
            + t.contextResolutionMs()
            + ",\"mergeMs\":"
            + t.mergeMs()
            + ",\"graphConstructionMs\":"
            + t.graphConstructionMs()
            + ",\"totalMs\":"
            + t.totalMs()
            + ",\"cacheHits\":"
            + t.cacheHits()
            + ",\"cacheMisses\":"
            + t.cacheMisses()
            + "}";
    return "{\"_type\":\"summary\",\"success\":"
        + meta.success()
        + ",\"contextCount\":"
        + meta.contextCount()
        + ",\"finalContextCount\":"
        + meta.finalContextCount()
        + ",\"quadCount\":"
        + meta.quadCount()
        + ",\"timings\":"
        + timingsJson
        + ",\"traceId\":"
        + traceJson
        + "}";
  }

  private static String errorLine(Exception e) {
    String msg =
        (e.getMessage() != null ? e.getMessage() : "Unknown error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    return "{\"_type\":\"error\",\"message\":\"" + msg + "\"}";
  }
}
