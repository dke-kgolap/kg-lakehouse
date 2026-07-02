package at.jku.dke.bigkgolap.inference.service;

import at.jku.dke.bigkgolap.engine.Engine;
import at.jku.dke.bigkgolap.inference.cache.DerivedCache;
import at.jku.dke.bigkgolap.model.CubeSchema;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.reasoning.InferenceEngine;
import at.jku.dke.bigkgolap.reasoning.ReasonerProfile;
import at.jku.dke.bigkgolap.reasoning.TBoxRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.springframework.stereotype.Service;

/**
 * Core inference: assembles the TBox for a context's engine, runs the rule engine over the supplied
 * base graph, and returns the derived triples (cached per {@code (schema, context, tboxVersion)}).
 */
@Service
public class ContextInferenceService {

  private final SchemaRepository schemas;
  private final List<Engine> engines;
  private final TBoxRegistry tboxRegistry;
  private final InferenceEngine inferenceEngine;
  private final DerivedCache derivedCache;

  private final ConcurrentHashMap<String, String> tboxVersionCache = new ConcurrentHashMap<>();

  public ContextInferenceService(
      SchemaRepository schemas,
      List<Engine> engines,
      TBoxRegistry tboxRegistry,
      InferenceEngine inferenceEngine,
      DerivedCache derivedCache) {
    this.schemas = schemas;
    this.engines = engines;
    this.tboxRegistry = tboxRegistry;
    this.inferenceEngine = inferenceEngine;
    this.derivedCache = derivedCache;
  }

  public record InferenceResult(List<String> derivedTriples, boolean fromCache) {}

  public InferenceResult infer(
      String schemaId, String contextId, String engineId, List<String> baseQuads) {
    CubeSchema schema = schemas.get(schemaId);
    if (schema == null) {
      throw new IllegalArgumentException("Unknown schema '" + schemaId + "'");
    }
    Engine engine = engineById(engineId);

    Model tbox = tboxRegistry.tboxFor(schema, engine);
    ReasonerProfile profile = tboxRegistry.profileFor(schema, engine);
    String version = tboxVersion(schemaId, engineId, tbox);

    String key = schemaId + ":" + contextId + ":" + version;
    Optional<List<String>> cached = derivedCache.get(key);
    if (cached.isPresent()) {
      return new InferenceResult(cached.get(), true);
    }

    Model base = parse(baseQuads);
    Model derived = inferenceEngine.infer(engine, base, tbox, profile);
    List<String> lines = toNTriples(derived);

    derivedCache.put(key, lines);
    return new InferenceResult(lines, false);
  }

  private Engine engineById(String engineId) {
    for (Engine e : engines) {
      if (e.id().equalsIgnoreCase(engineId)) {
        return e;
      }
    }
    throw new IllegalArgumentException("No engine '" + engineId + "' on the classpath");
  }

  /** Parses base quads (N-Quads, possibly module-tagged) into a single union model. */
  private Model parse(List<String> baseQuads) {
    Dataset ds = DatasetFactory.create();
    byte[] bytes = String.join("\n", baseQuads).getBytes(StandardCharsets.UTF_8);
    RDFParser.source(new ByteArrayInputStream(bytes)).lang(Lang.NQUADS).parse(ds);
    Model union = ModelFactory.createDefaultModel();
    union.add(ds.getDefaultModel());
    var names = ds.listNames();
    while (names.hasNext()) {
      union.add(ds.getNamedModel(names.next()));
    }
    return union;
  }

  private static List<String> toNTriples(Model model) {
    var out = new ByteArrayOutputStream();
    RDFDataMgr.write(out, model, Lang.NTRIPLES);
    List<String> lines = new ArrayList<>();
    for (String line : out.toString(StandardCharsets.UTF_8).split("\n")) {
      String trimmed = line.strip();
      if (!trimmed.isEmpty()) {
        lines.add(trimmed);
      }
    }
    return lines;
  }

  /** Stable short hash of the assembled TBox, so ontology/ruleset changes invalidate the cache. */
  private String tboxVersion(String schemaId, String engineId, Model tbox) {
    return tboxVersionCache.computeIfAbsent(
        schemaId + ":" + engineId,
        k -> {
          List<String> lines = toNTriples(tbox);
          lines.sort(null);
          try {
            byte[] digest =
                MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(Arrays.copyOf(digest, 8));
          } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
          }
        });
  }
}
