package at.jku.dke.bigkgolap.reasoning;

import at.jku.dke.bigkgolap.engine.Engine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;

/**
 * {@link InferenceEngine} that runs Jena's {@code GenericRuleReasoner} loaded from the engine's
 * declared rule file ({@link Engine#rulesResource()}). Replaces Jena's built-in RDFS/OWL closure
 * with the lakehouse's own rule set (see {@code rulesets/{engine}/}).
 *
 * <p>The {@link ReasonerProfile} parameter is intentionally ignored: the rule set is the closure
 * definition. Throws {@link IllegalStateException} if the engine declares no rule file.
 *
 * <p>Output is filtered through {@link RdfsReasonerService#DEFAULT_NOISE_FILTER} for consistency
 * with the legacy {@link JenaRdfsOwlInferenceEngine}: asserted statements, schema-level axioms,
 * meta-class typings, and vocabulary-term tautologies are dropped.
 */
public final class GenericRuleInferenceEngine implements InferenceEngine {

  private final Predicate<Statement> noiseFilter;
  private final ConcurrentHashMap<String, List<Rule>> rulesCache = new ConcurrentHashMap<>();

  public GenericRuleInferenceEngine() {
    this(RdfsReasonerService.DEFAULT_NOISE_FILTER);
  }

  public GenericRuleInferenceEngine(Predicate<Statement> noiseFilter) {
    this.noiseFilter = noiseFilter;
  }

  @Override
  public Model infer(Engine engine, Model base, Model tbox, ReasonerProfile profile) {
    List<Rule> rules = rulesCache.computeIfAbsent(engine.id(), k -> loadRules(engine));
    GenericRuleReasoner reasoner = new GenericRuleReasoner(rules);
    var inf = ModelFactory.createInfModel(reasoner.bindSchema(tbox), base);

    // InfModel.listStatements() returns TBox + base + derived. Filter both asserted sources so
    // the result contains only deductions; the noise filter then drops schema-level leftovers
    // (rdf:type to meta-classes, rdfs:subClassOf, etc.) for consistency with the legacy backend.
    Model result = ModelFactory.createDefaultModel();
    inf.listStatements()
        .forEachRemaining(
            stmt -> {
              if (base.contains(stmt) || tbox.contains(stmt)) {
                return;
              }
              if (noiseFilter.test(stmt)) {
                result.add(stmt);
              }
            });
    return result;
  }

  private static List<Rule> loadRules(Engine engine) {
    String resource = engine.rulesResource();
    if (resource == null) {
      throw new IllegalStateException(
          "Engine '%s' declares no rulesResource(); cannot run GenericRuleInferenceEngine"
              .formatted(engine.id()));
    }
    try (InputStream in = engine.getClass().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException(
            "Rule resource '%s' not found on classloader of engine '%s'"
                .formatted(resource, engine.id()));
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        return Rule.parseRules(Rule.rulesParserFromReader(reader));
      }
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to load rule resource '%s' for engine '%s'".formatted(resource, engine.id()), e);
    }
  }
}
