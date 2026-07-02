package at.jku.dke.bigkgolap.query.api;

import at.jku.dke.bigkgolap.grpc.GraphCacheRequest;
import at.jku.dke.bigkgolap.grpc.GraphQueryServiceGrpc;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.query.api.dto.ClearCacheResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * admin endpoint for benchmark cold-cache mode. Iterates registered schemas via {@link
 * SchemaRepository}, issues {@code ClearCache(schemaId, contextIds=[])} to graph-service per
 * schema. Production deployments disable via {@code lakehouse.admin.enabled=false}.
 */
@RestController
@ConditionalOnProperty(
    name = "lakehouse.admin.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AdminController {

  private static final Logger log = LoggerFactory.getLogger(AdminController.class);

  private final SchemaRepository schemas;
  private final GraphQueryServiceGrpc.GraphQueryServiceBlockingStub stub;

  public AdminController(
      SchemaRepository schemas, GraphQueryServiceGrpc.GraphQueryServiceBlockingStub stub) {
    this.schemas = schemas;
    this.stub = stub;
  }

  @PostMapping("/admin/cache/clear")
  public ResponseEntity<ClearCacheResponse> clearAllCaches() {
    List<String> cleared = List.copyOf(schemas.list());
    for (String schemaId : cleared) {
      GraphCacheRequest request = GraphCacheRequest.newBuilder().setSchemaId(schemaId).build();
      var response = stub.clearCache(request);
      log.info(
          "Cleared cache for schema={}: response.clearedCount={}",
          schemaId,
          response.getClearedCount());
    }
    return ResponseEntity.ok(new ClearCacheResponse(cleared));
  }
}
