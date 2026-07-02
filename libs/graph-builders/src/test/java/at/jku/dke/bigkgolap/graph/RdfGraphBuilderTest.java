package at.jku.dke.bigkgolap.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.apache.jena.query.Dataset;
import org.junit.jupiter.api.Test;

class RdfGraphBuilderTest {

  @Test
  void addTripleResourceObjectWritesOneTriple() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "urn:o");
    assertThat(b.tripleCount()).isEqualTo(1L);
  }

  @Test
  void addTripleIsLiteralWritesAPlainLiteral() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "hello", true, null, null);
    var turtle = new String(b.serialize("TURTLE"), StandardCharsets.UTF_8);
    assertThat(turtle).contains("\"hello\"");
  }

  @Test
  void addTripleWithDatatypeWritesATypedLiteral() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "42", true, "http://www.w3.org/2001/XMLSchema#int", null);
    var turtle = new String(b.serialize("TURTLE"), StandardCharsets.UTF_8);
    assertThat(turtle).contains("\"42\"");
    assertThat(turtle).contains("XMLSchema#int");
  }

  @Test
  void addTripleWithLangWritesALanguageTaggedLiteral() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "hallo", true, null, "de");
    var turtle = new String(b.serialize("TURTLE"), StandardCharsets.UTF_8);
    assertThat(turtle).contains("\"hallo\"@de");
  }

  @Test
  void addTripleFourArgOverloadAddsToDefaultGraph() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "urn:o", "urn:graph");
    assertThat(b.tripleCount()).isEqualTo(1L);
  }

  @Test
  void serializeTurtleReturnsNonEmptyUtf8() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "urn:o");
    var bytes = b.serialize("TURTLE");
    assertThat(bytes).isNotEmpty();
    assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("urn:s");
  }

  @Test
  void serializeUnknownFormatThrowsIllegalArgumentException() {
    var b = new RdfGraphBuilder();
    assertThatThrownBy(() -> b.serialize("BOGUS-FORMAT"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void buildReturnsADataset() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "urn:o");
    var built = b.build();
    assertThat(built).isInstanceOf(Dataset.class);
    assertThat(((Dataset) built).getDefaultModel().size()).isEqualTo(1L);
  }

  @Test
  void assertedAndDerivedTriplesLandInSeparateModels() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "urn:o");
    b.addDerived("urn:s", "urn:p", "urn:super");

    assertThat(b.assertedModel().size()).isEqualTo(1L);
    assertThat(b.derivedModel().size()).isEqualTo(1L);
    assertThat(b.tripleCount()).isEqualTo(2L);
  }

  @Test
  void serializeNQuadsKeepsDerivedTriplesInTheirNamedGraph() {
    var b = new RdfGraphBuilder();
    b.addTriple("urn:s", "urn:p", "urn:o");
    b.addDerived("urn:s", "urn:p", "urn:super");
    var nq = new String(b.serialize("N-QUADS"), StandardCharsets.UTF_8);
    // derived triple is a quad tagged with the derived graph; asserted triple is a 3-term default
    assertThat(nq).contains(RdfGraphBuilder.DERIVED_GRAPH);
    assertThat(nq).contains("urn:super");
  }
}
