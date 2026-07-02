package at.jku.dke.bigkgolap.engine.fakes;

import at.jku.dke.bigkgolap.engine.GraphBuilder;
import at.jku.dke.bigkgolap.engine.Mapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class FakeMapper implements Mapper {

  @Override
  public void map(InputStream input, GraphBuilder builder) {
    byte[] bytes;
    try {
      bytes = input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    var payload = new String(bytes, StandardCharsets.UTF_8).strip();
    builder.addTriple(
        "urn:fake:subject",
        "urn:fake:hasContent",
        payload.isEmpty() ? "empty" : payload,
        true,
        null,
        null);
  }
}
