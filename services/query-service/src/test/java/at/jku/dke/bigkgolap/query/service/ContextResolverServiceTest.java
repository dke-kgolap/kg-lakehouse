package at.jku.dke.bigkgolap.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.index.testing.InMemoryIndexRepository;
import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.Hierarchy;
import at.jku.dke.bigkgolap.model.HierarchyFactory;
import at.jku.dke.bigkgolap.model.Member;
import at.jku.dke.bigkgolap.model.SliceDiceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextResolverServiceTest {

  private CubeSchema schema;
  private InMemoryIndexRepository index;
  private ContextResolverService resolver;

  @BeforeEach
  void setUp() {
    schema = CubeSchema.fromYaml(getClass().getResourceAsStream("/fixtures/atm.yaml"));
    index = new InMemoryIndexRepository();
    resolver = new ContextResolverService(index);
  }

  @Test
  void delegatesToIndexRepository() {
    var year = schema.locate("time", "year");
    var ctx =
        Context.of(List.of(HierarchyFactory.create(new Member(year, "2025"), schema)), schema);
    index.upsertContext(schema, ctx);

    var all = resolver.resolveSpecific(schema, SliceDiceContext.empty());
    assertThat(all).containsExactly(ctx);
  }

  @Test
  void generalResolutionMatchesStoredCoarserContexts() {
    var year = schema.locate("time", "year");
    var month = schema.locate("time", "month");
    var coarse = Context.of(List.of(Hierarchy.of(new Member(year, "2018"))), schema);
    index.upsertContext(schema, coarse);

    var sliceDice =
        SliceDiceContext.of(
            List.of(HierarchyFactory.create(new Member(month, "2018-02"), schema)), schema);
    var general = resolver.resolveGeneralIds(schema, sliceDice);
    assertThat(general).containsExactly(coarse.id());
  }
}
