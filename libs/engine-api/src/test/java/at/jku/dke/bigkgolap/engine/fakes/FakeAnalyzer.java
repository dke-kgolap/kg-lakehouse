package at.jku.dke.bigkgolap.engine.fakes;

import at.jku.dke.bigkgolap.engine.Analyzer;
import at.jku.dke.bigkgolap.engine.AnalyzerResult;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.Member;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

public class FakeAnalyzer implements Analyzer {

  @Override
  public AnalyzerResult analyze(InputStream input, CubeSchema schema) {
    byte[] bytes;
    try {
      bytes = input.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    var firstDim = schema.dimensions().values().stream().findFirst().orElse(null);
    if (firstDim == null) {
      return AnalyzerResult.EMPTY;
    }

    var rootLevel = firstDim.rootLevel();
    var raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).strip();
    var value = raw.isEmpty() ? "fake" : raw;
    var hierarchy = Hierarchy.of(new Member(rootLevel, value));

    return new AnalyzerResult(
        java.util.List.of(Map.of(firstDim.name(), hierarchy)),
        Map.of("byteCount", String.valueOf(bytes.length)));
  }
}
