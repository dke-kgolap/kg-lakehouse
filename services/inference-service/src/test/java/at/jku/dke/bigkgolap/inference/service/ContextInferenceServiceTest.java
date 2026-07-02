package at.jku.dke.bigkgolap.inference.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.jku.dke.bigkgolap.engine.Engines;
import at.jku.dke.bigkgolap.inference.cache.DerivedCache;
import at.jku.dke.bigkgolap.model.repository.InMemorySchemaRepository;
import at.jku.dke.bigkgolap.reasoning.JenaRdfsOwlInferenceEngine;
import at.jku.dke.bigkgolap.reasoning.TBoxRegistry;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextInferenceServiceTest {

  private static final String BASE = "http://example.org/bigkgolap/atm/aixm#";

  private MapDerivedCache cache;
  private ContextInferenceService service;

  @BeforeEach
  void setUp() throws Exception {
    var schemas = new InMemorySchemaRepository();
    try (InputStream in = getClass().getResourceAsStream("/fixtures/atm.yaml")) {
      schemas.registerYaml(in);
    }
    cache = new MapDerivedCache();
    service =
        new ContextInferenceService(
            schemas,
            Engines.discover(),
            new TBoxRegistry(),
            new JenaRdfsOwlInferenceEngine(),
            cache);
  }

  @Test
  void infersSuperTypesFromBaseGraph() {
    // Topic taxonomy in the test fixture: AircraftStand ⊑ Apron ⊑ AirportHeliport.
    List<String> base =
        List.of("<" + BASE + "stand-1> <" + rdfType() + "> <" + BASE + "AircraftStand> .");

    var result = service.infer("atm", "ctx-1", "aixm", base);

    assertThat(result.fromCache()).isFalse();
    assertThat(result.derivedTriples()).anyMatch(t -> t.contains(BASE + "Apron>"));
    assertThat(result.derivedTriples()).anyMatch(t -> t.contains(BASE + "AirportHeliport>"));
    // base triple is not echoed back as derived
    assertThat(result.derivedTriples()).noneMatch(t -> t.contains(BASE + "AircraftStand>"));
  }

  @Test
  void secondCallForSameContextHitsCache() {
    List<String> base =
        List.of("<" + BASE + "stand-1> <" + rdfType() + "> <" + BASE + "AircraftStand> .");

    service.infer("atm", "ctx-1", "aixm", base);
    var second = service.infer("atm", "ctx-1", "aixm", base);

    assertThat(second.fromCache()).isTrue();
    assertThat(second.derivedTriples()).anyMatch(t -> t.contains(BASE + "Apron>"));
  }

  private static String rdfType() {
    return "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
  }

  private static final class MapDerivedCache implements DerivedCache {
    private final Map<String, List<String>> store = new HashMap<>();

    @Override
    public Optional<List<String>> get(String key) {
      return Optional.ofNullable(store.get(key));
    }

    @Override
    public void put(String key, List<String> derivedTriples) {
      store.put(key, List.copyOf(derivedTriples));
    }
  }
}
