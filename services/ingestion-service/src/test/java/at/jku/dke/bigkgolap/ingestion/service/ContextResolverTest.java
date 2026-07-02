package at.jku.dke.bigkgolap.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.engine.AnalyzerResult;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextResolverTest {

  private CubeSchema schema;
  private ContextResolver resolver;

  @BeforeEach
  void setUp() {
    schema = CubeSchema.fromYaml(getClass().getResourceAsStream("/fixtures/atm.yaml"));
    resolver = new ContextResolver();
  }

  @Test
  void featureContextsPopulatedProducesOneContextPerFeature() {
    var day = schema.locate("time", "day");
    var location = schema.locate("location", "location");
    var feature = schema.locate("topic", "feature");

    var standCtx =
        Map.of(
            "time", HierarchyFactory.create(day, "2025-01-15", schema),
            "location", HierarchyFactory.create(location, "LOWW", schema),
            "topic", HierarchyFactory.create(feature, "AircraftStand", schema));
    var apronCtx =
        Map.of(
            "time", HierarchyFactory.create(day, "2025-01-16", schema),
            "location", HierarchyFactory.create(location, "LOWS", schema),
            "topic", HierarchyFactory.create(feature, "ApronElement", schema));

    var contexts =
        resolver.resolve(new AnalyzerResult(List.of(standCtx, apronCtx), Map.of()), schema);

    assertThat(contexts).hasSize(2);
    assertThat(contexts.stream().map(c -> c.id()).toList()).doesNotHaveDuplicates();
  }

  @Test
  void partialFeatureContextFillsMissingDimensionsWithAllHierarchy() {
    var feature = schema.locate("topic", "feature");
    var onlyTopic = Map.of("topic", HierarchyFactory.create(feature, "AircraftStand", schema));

    var contexts = resolver.resolve(new AnalyzerResult(List.of(onlyTopic), Map.of()), schema);

    assertThat(contexts).hasSize(1);
    var ctx = contexts.iterator().next();
    assertThat(ctx.getHierarchy("time")).isEqualTo(Hierarchy.all("time"));
    assertThat(ctx.getHierarchy("location")).isEqualTo(Hierarchy.all("location"));
  }

  @Test
  void emptyAnalyzerResultYieldsEmptySet() {
    assertThat(resolver.resolve(AnalyzerResult.EMPTY, schema)).isEmpty();
  }

  @Test
  void ofFlatResultWithOneHierarchyPerDimensionProducesASingleContext() {
    var location = schema.locate("location", "location");
    var feature = schema.locate("topic", "feature");
    var flat =
        AnalyzerResult.ofFlat(
            Set.of(
                HierarchyFactory.create(location, "LOWW", schema),
                HierarchyFactory.create(feature, "AircraftStand", schema)));

    var contexts = resolver.resolve(flat, schema);

    assertThat(contexts).hasSize(1);
    assertThat(contexts.iterator().next().getHierarchy("time")).isEqualTo(Hierarchy.all("time"));
  }
}
