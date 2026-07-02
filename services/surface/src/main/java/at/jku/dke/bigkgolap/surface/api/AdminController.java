package at.jku.dke.bigkgolap.surface.api;

import at.jku.dke.bigkgolap.surface.api.dto.ClearCacheResponse;
import at.jku.dke.bigkgolap.surface.service.QueryServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * benchmark-facing admin endpoint. {@code PUT /api/admin/cache/clear} routes to query-service which
 * iterates registered schemas and issues a {@code ClearCache(schema, contextIds=[])} gRPC call per
 * schema. Disabled by setting {@code lakehouse.admin.enabled=false} (the controller bean is then
 * not registered).
 */
@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(
    name = "lakehouse.admin.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AdminController {

  private final QueryServiceClient client;

  public AdminController(QueryServiceClient client) {
    this.client = client;
  }

  @PutMapping("/cache/clear")
  public ResponseEntity<ClearCacheResponse> clearAllCaches() {
    return ResponseEntity.ok(client.clearAllCaches());
  }
}
