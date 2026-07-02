package at.jku.dke.bigkgolap.surface.api;

import at.jku.dke.bigkgolap.surface.api.dto.IngestionResponse;
import at.jku.dke.bigkgolap.surface.service.IngestionGateway;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/schemas/{schemaId}/ingest")
public class IngestionController {

  private final IngestionGateway gateway;

  public IngestionController(IngestionGateway gateway) {
    this.gateway = gateway;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<IngestionResponse> ingest(
      @PathVariable String schemaId, @RequestPart("file") MultipartFile file) {
    IngestionResponse response = gateway.ingest(schemaId, file);
    return ResponseEntity.accepted().body(response);
  }
}
