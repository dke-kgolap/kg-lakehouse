package at.jku.dke.bigkgolap.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.jku.dke.bigkgolap.engine.fakes.FakeEngine;
import at.jku.dke.bigkgolap.engine.fakes.RecordingGraphBuilder;
import at.jku.dke.bigkgolap.model.CubeSchema;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnginesTest {

  private static final String MINI_SCHEMA_YAML =
      """
      schema:
        id: mini
        dimensions:
          tag:
            levels:
              - { name: family, depth: 0, rollup_to: null, rollup_function: null }
      """;

  private final CubeSchema schema =
      CubeSchema.fromYaml(
          new ByteArrayInputStream(MINI_SCHEMA_YAML.getBytes(StandardCharsets.UTF_8)));

  @Test
  void discoverFindsEnginesRegisteredViaMetaInfServices() {
    var engines = Engines.discover();
    assertThat(engines).hasSize(1);
    assertThat(engines.getFirst()).isInstanceOf(FakeEngine.class);
  }

  @Test
  void findResolvesByMediaTypeCaseInsensitively() {
    assertThat(Engines.find("application/x-fake")).isInstanceOf(FakeEngine.class);
    assertThat(Engines.find("APPLICATION/X-FAKE")).isInstanceOf(FakeEngine.class);
    assertThat(Engines.find("application/unknown")).isNull();
  }

  @Test
  void byIdResolvesCaseInsensitively() {
    assertThat(Engines.byId("fake")).isInstanceOf(FakeEngine.class);
    assertThat(Engines.byId("FAKE")).isInstanceOf(FakeEngine.class);
    assertThat(Engines.byId("nope")).isNull();
  }

  @Test
  void analyzerRoundTripsThroughEngineAndSurfacesHierarchiesAndMetadata() {
    var engine = Engines.byId("fake");
    assertThat(engine).isNotNull();
    var result =
        engine
            .analyzer()
            .analyze(new ByteArrayInputStream("austria".getBytes(StandardCharsets.UTF_8)), schema);
    assertThat(result.hierarchies()).hasSize(1);
    var hierarchy = result.hierarchies().iterator().next();
    assertThat(hierarchy.dimension()).isEqualTo("tag");
    assertThat(hierarchy.leafMember().value()).isEqualTo("austria");
    assertThat(result.metadata()).containsEntry("byteCount", "7");
  }

  @Test
  void mapperWritesTriplesIntoTheSuppliedBuilder() {
    var engine = Engines.byId("fake");
    assertThat(engine).isNotNull();
    var builder = new RecordingGraphBuilder();
    engine
        .mapper()
        .map(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), builder);
    assertThat(builder.tripleCount()).isEqualTo(1L);
    var captured = builder.captured().getFirst();
    assertThat(captured.subject()).isEqualTo("urn:fake:subject");
    assertThat(captured.obj()).isEqualTo("hello");
    assertThat(captured.isLiteral()).isTrue();
  }

  @Test
  void analyzerResultEmptyEqualsAnExplicitlyEmptyResult() {
    assertThat(AnalyzerResult.EMPTY).isEqualTo(new AnalyzerResult());
    assertThat(AnalyzerResult.EMPTY.hierarchies()).isEmpty();
    assertThat(AnalyzerResult.EMPTY.metadata()).isEmpty();
  }
}

class RecordingGraphBuilderTest {

  @Test
  void bothAddTripleOverloadsAppendToTheSameList() {
    var b = new RecordingGraphBuilder();
    b.addTriple("s1", "p1", "o1", true, "xsd:string", null);
    b.addTriple("s2", "p2", "o2", "urn:g");
    assertThat(b.tripleCount()).isEqualTo(2L);
    @SuppressWarnings("unchecked")
    var built = (List<RecordingGraphBuilder.Triple>) b.build();
    assertThat(built.stream().map(RecordingGraphBuilder.Triple::subject).toList())
        .containsExactly("s1", "s2");
    assertThat(built.get(0).datatype()).isEqualTo("xsd:string");
    assertThat(built.get(1).graph()).isEqualTo("urn:g");
  }

  @Test
  void serializeTextPlainReturnsOneLinePerTriple() {
    var b = new RecordingGraphBuilder();
    b.addTriple("s1", "p1", "o1");
    b.addTriple("s2", "p2", "o2");
    var text = new String(b.serialize("text/plain"), StandardCharsets.UTF_8);
    assertThat(text.lines().toList()).containsExactly("s1 p1 o1", "s2 p2 o2");
  }

  @Test
  void serializeRejectsUnknownFormats() {
    var b = new RecordingGraphBuilder();
    assertThatThrownBy(() -> b.serialize("application/n-quads"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
