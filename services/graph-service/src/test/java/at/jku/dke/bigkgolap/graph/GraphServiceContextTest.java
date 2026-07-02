package at.jku.dke.bigkgolap.graph;

import at.jku.dke.bigkgolap.cache.GraphCache;
import at.jku.dke.bigkgolap.graph.fakes.InMemoryGraphCache;
import at.jku.dke.bigkgolap.graph.fakes.TestProfileConfig;
import at.jku.dke.bigkgolap.graph.grpc.GraphQueryServiceImpl;
import at.jku.dke.bigkgolap.graph.service.GraphConstructionService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestProfileConfig.class)
class GraphServiceContextTest {

  @Autowired GraphCache graphCache;

  @Autowired GraphConstructionService construction;

  @Autowired GraphQueryServiceImpl grpcImpl;

  @Test
  void contextLoadsWithTestProfileBeansWired() {
    Assertions.assertThat(graphCache).isInstanceOf(InMemoryGraphCache.class);
    Assertions.assertThat(construction).isNotNull();
    Assertions.assertThat(grpcImpl).isNotNull();
  }
}
