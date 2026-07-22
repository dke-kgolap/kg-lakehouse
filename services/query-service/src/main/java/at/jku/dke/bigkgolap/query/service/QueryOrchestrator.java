package at.jku.dke.bigkgolap.query.service;

import at.jku.dke.bigkgolap.model.Context;
import at.jku.dke.bigkgolap.model.GraphRepresentation;
import at.jku.dke.bigkgolap.model.repository.SchemaRepository;
import at.jku.dke.bigkgolap.observability.MeterNames;
import at.jku.dke.bigkgolap.observability.log.QueryLogger;
import at.jku.dke.bigkgolap.query.api.dto.QueryResponse;
import at.jku.dke.bigkgolap.query.api.dto.QueryTimings;
import at.jku.dke.bigkgolap.query.exception.InvalidQueryException;
import at.jku.dke.bigkgolap.query.exception.QueryTimeoutException;
import at.jku.dke.bigkgolap.query.util.QuadHash;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QueryOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(QueryOrchestrator.class);
  private static final long NANOS_PER_MS = 1_000_000L;
  private static final long GLOBAL_TIMEOUT_PADDING_S = 5L;

  // CKR / OLAP metadata vocabulary (mirrors libs/graph-builders CkrVocabulary) used to express the
  // asserted/derived module split and the coverage hierarchy on the wire.
  private static final String CKR_NS = "http://dkm.fbk.eu/ckr/meta#";
  private static final String OLAP_NS = "http://dkm.fbk.eu/ckr/olap-model#";
  private static final String HAS_ASSERTED_MODULE = CKR_NS + "hasAssertedModule";
  private static final String HAS_MODULE = CKR_NS + "hasModule";
  private static final String CLOSURE_OF = CKR_NS + "closureOf";
  private static final String DERIVED_FROM = CKR_NS + "derivedFrom";
  private static final String COVERS = OLAP_NS + "covers";
  private static final String MOD_SUFFIX = "-mod";
  private static final String INF_SUFFIX = "-inf";

  private final SchemaRepository schemas;
  private final ContextResolverService resolver;
  private final MergeAndPropagateService merger;
  private final GraphQueryServiceClient graphClient;
  private final InferenceServiceClient inferenceClient;
  private final ExecutorService executor;
  private final String graphBaseUri;
  private final long fanoutTimeoutSeconds;
  private final int batchSize;
  private final MeterRegistry meterRegistry;
  private final Tracer tracer;
  private final QueryLogger queryLogger;

  private final Timer resolveTimer;
  private final Timer mergeTimer;
  private final Timer totalTimer;
  private final DistributionSummary contextsSummary;
  private final DistributionSummary quadsSummary;

  public QueryOrchestrator(
      SchemaRepository schemas,
      ContextResolverService resolver,
      MergeAndPropagateService merger,
      GraphQueryServiceClient graphClient,
      InferenceServiceClient inferenceClient,
      @Qualifier("graphFanoutExecutor") ExecutorService executor,
      @Value("${lakehouse.query.graph.base-uri:urn:lakehouse:}") String graphBaseUri,
      @Value("${lakehouse.query.fanout.timeout-seconds:60}") long fanoutTimeoutSeconds,
      @Value("${lakehouse.query.fanout.batch-size:16}") int batchSize,
      MeterRegistry meterRegistry,
      Tracer tracer,
      QueryLogger queryLogger) {
    this.schemas = schemas;
    this.resolver = resolver;
    this.merger = merger;
    this.graphClient = graphClient;
    this.inferenceClient = inferenceClient;
    this.executor = executor;
    this.graphBaseUri = graphBaseUri;
    this.fanoutTimeoutSeconds = fanoutTimeoutSeconds;
    this.batchSize = Math.max(1, batchSize);
    this.meterRegistry = meterRegistry;
    this.tracer = tracer;
    this.queryLogger = queryLogger;

    this.resolveTimer =
        Timer.builder(MeterNames.QUERY_CONTEXT_RESOLUTION_DURATION).register(meterRegistry);
    this.mergeTimer = Timer.builder(MeterNames.QUERY_MERGE_DURATION).register(meterRegistry);
    this.totalTimer = Timer.builder(MeterNames.QUERY_TOTAL_DURATION).register(meterRegistry);
    this.contextsSummary =
        DistributionSummary.builder(MeterNames.QUERY_CONTEXTS_COUNT).register(meterRegistry);
    this.quadsSummary =
        DistributionSummary.builder(MeterNames.QUERY_QUADS_COUNT).register(meterRegistry);
  }

  /** Collects all quads in memory — retained for tests and backward-compatible callers. */
  public QueryResponse execute(String schemaId, String queryText, ParsedQuery parsed) {
    Set<String> quads = ConcurrentHashMap.newKeySet();
    QueryResponse meta = executeStreaming(schemaId, queryText, parsed, quads::add);
    String data = String.join("\n", quads);
    return meta.withData(data, quads.size());
  }

  /**
   * Streams quads to {@code quadSink} as they arrive from graph-service fan-out. {@code quadSink}
   * may be called concurrently from multiple fan-out threads; callers must provide a thread-safe
   * implementation (e.g. synchronized on the output stream).
   *
   * <p>Returns a {@link QueryResponse} with {@link QueryResponse#data()} = "" and {@link
   * QueryResponse#quadCount()} reflecting the UNIQUE quads streamed — duplicates across contexts
   * are removed on the asserted (reasoning=false) path; the reasoning=true path is batched too but
   * remains un-deduped.
   */
  public QueryResponse executeStreaming(
      String schemaId, String queryText, ParsedQuery parsed, Consumer<String> quadSink) {
    var schema = schemas.get(schemaId);
    if (schema == null) {
      throw new InvalidQueryException("Unknown schema '" + schemaId + "'");
    }
    long t0 = System.nanoTime();

    long resolveStart = System.nanoTime();
    var specific = resolver.resolveSpecific(schema, parsed.sliceDice());
    var covering = resolver.resolveCovering(schema, parsed.sliceDice());
    long resolveNanos = System.nanoTime() - resolveStart;
    resolveTimer.record(resolveNanos, TimeUnit.NANOSECONDS);

    long mergeStart = System.nanoTime();
    var activeMerge =
        parsed.mergeLevels() != null && !parsed.mergeLevels().levels().isEmpty()
            ? parsed.mergeLevels()
            : null;
    var mp = merger.mergeAndPropagate(schema, specific, activeMerge);
    var knowledgeMap = new HashMap<String, Set<Context>>(mp.contextMap().size() + covering.size());
    knowledgeMap.putAll(mp.contextMap());
    // CKR propagation (Definition 3 (vi)): a covering context's module belongs to every cell it
    // covers, so it is attached to the final graphs of exactly the source cells it covers. A
    // context above the whole scope reaches every final; a mixed-granularity context (finer than
    // the scope in one dimension, coarser in another) reaches only the finals of its descendants.
    for (Context cov : covering) {
      var attach = new HashSet<Context>();
      for (Context source : specific) {
        if (source.rollsUpTo(cov, schema)) {
          attach.addAll(mp.contextMap().get(source.id()));
        }
      }
      if (!attach.isEmpty()) {
        knowledgeMap.put(cov.id(), attach);
      }
    }
    long mergeNanos = System.nanoTime() - mergeStart;
    mergeTimer.record(mergeNanos, TimeUnit.NANOSECONDS);

    // On the asserted (reasoning=false) path the per-context fan-out legitimately re-emits shared
    // context graphs' quads (CKR inheritance / general-ids), so we dedup on the EMITTED quad. The
    // seen-set stores a 128-bit hash per unique quad rather than the full string, bounding memory
    // to
    // the unique result. The reasoning=true path is now batched too, but stays un-deduped
    // (application-side, descoped).
    final Set<QuadHash> emitted = parsed.reasoning() ? null : ConcurrentHashMap.newKeySet();
    Consumer<String> dedupSink =
        emitted == null
            ? quadSink
            : q -> {
              if (emitted.add(QuadHash.of(q))) {
                quadSink.accept(q);
              }
            };
    FanOutResult fanOut =
        fanOutStreaming(schemaId, knowledgeMap, mp.contextMap().keySet(), parsed, dedupSink);
    long totalNanos = System.nanoTime() - t0;
    totalTimer.record(totalNanos, TimeUnit.NANOSECONDS);

    // Deduped emit count on the asserted path; raw fan-out count when reasoning is on.
    long emittedCount = emitted == null ? fanOut.quadCount() : emitted.size();

    contextsSummary.record(knowledgeMap.size());
    quadsSummary.record(emittedCount);

    var timings =
        new QueryTimings(
            resolveNanos / NANOS_PER_MS,
            mergeNanos / NANOS_PER_MS,
            (totalNanos - resolveNanos - mergeNanos) / NANOS_PER_MS,
            totalNanos / NANOS_PER_MS,
            fanOut.cacheHits(),
            fanOut.cacheMisses());

    String traceId = null;
    var span = tracer.currentSpan();
    if (span != null && span.context() != null) {
      traceId = span.context().traceId();
    }

    log.info(
        "query schema={} contexts={} final={} quads={} cacheHits={} cacheMisses={} total_ms={} traceId={}",
        schemaId,
        knowledgeMap.size(),
        mp.finalContexts().size(),
        emittedCount,
        fanOut.cacheHits(),
        fanOut.cacheMisses(),
        timings.totalMs(),
        traceId);

    queryLogger.log(
        schemaId,
        queryText,
        timings.contextResolutionMs(),
        timings.graphConstructionMs(),
        timings.mergeMs(),
        timings.totalMs(),
        knowledgeMap.size(),
        emittedCount,
        fanOut.cacheHits(),
        fanOut.cacheMisses(),
        true,
        null);

    return new QueryResponse(
        true, knowledgeMap.size(), mp.finalContexts().size(), emittedCount, "", timings, traceId);
  }

  public record FanOutResult(long quadCount, int cacheHits, int cacheMisses) {}

  private FanOutResult fanOutStreaming(
      String schemaId,
      Map<String, Set<Context>> knowledgeMap,
      Set<String> leafIds,
      ParsedQuery parsed,
      Consumer<String> quadSink) {
    if (knowledgeMap.isEmpty()) {
      return new FanOutResult(0L, 0, 0);
    }
    var quadCount = new AtomicLong(0);
    var cacheHits = new AtomicInteger(0);
    var cacheMisses = new AtomicInteger(0);
    Set<Throwable> errors = ConcurrentHashMap.newKeySet();

    if (parsed.reasoning()) {
      return fanOutBatchedReasoning(
          schemaId,
          knowledgeMap,
          leafIds,
          parsed,
          quadSink,
          quadCount,
          cacheHits,
          cacheMisses,
          errors);
    }
    return fanOutBatched(
        schemaId, knowledgeMap, parsed, quadSink, quadCount, cacheHits, cacheMisses, errors);
  }

  /** One reasoning context's resolved work: which final contexts it belongs to and their URIs. */
  private record ReasoningUnit(
      String contextId, Set<Context> finalContexts, List<String> graphUris) {}

  private FanOutResult fanOutBatchedReasoning(
      String schemaId,
      Map<String, Set<Context>> knowledgeMap,
      Set<String> leafIds,
      ParsedQuery parsed,
      Consumer<String> quadSink,
      AtomicLong quadCount,
      AtomicInteger cacheHits,
      AtomicInteger cacheMisses,
      Set<Throwable> errors) {
    // Already on the reasoning path; infer means RDF output with a wired inference client.
    boolean infer = parsed.representation() == GraphRepresentation.RDF && inferenceClient != null;

    // Membership + asserted-module metadata is client-side per context (as on the asserted path).
    List<ReasoningUnit> units = new ArrayList<>(knowledgeMap.size());
    for (var entry : knowledgeMap.entrySet()) {
      Set<Context> finalContexts = entry.getValue();
      List<String> graphUris = finalContexts.stream().map(this::graphUriFor).toList();
      addContextMembership(quadSink, quadCount, graphUris, finalContexts, parsed.representation());
      units.add(new ReasoningUnit(entry.getKey(), finalContexts, graphUris));
    }

    List<List<ReasoningUnit>> batches = new ArrayList<>();
    for (int i = 0; i < units.size(); i += batchSize) {
      batches.add(units.subList(i, Math.min(i + batchSize, units.size())));
    }
    var pending = new AtomicInteger(batches.size());
    var done = new CountDownLatch(batches.size());
    for (var batch : batches) {
      executor.submit(
          () -> {
            try {
              List<GraphQueryServiceClient.ContextQuerySpec> specs =
                  batch.stream()
                      .map(
                          u ->
                              new GraphQueryServiceClient.ContextQuerySpec(
                                  u.contextId(), u.graphUris()))
                      .toList();
              var grouped =
                  graphClient.queryQuadsBatchGrouped(schemaId, specs, parsed.representation());
              cacheHits.addAndGet(grouped.cacheHits());
              cacheMisses.addAndGet(grouped.cacheMisses());

              // Emit each context's base quads (its -mod graph).
              for (var u : batch) {
                for (String quad :
                    grouped.linesByContext().getOrDefault(u.contextId(), List.of())) {
                  quadSink.accept(quad);
                  quadCount.incrementAndGet();
                }
              }

              if (infer) {
                List<InferenceServiceClient.InferContextSpec> inferSpecs =
                    batch.stream()
                        .map(
                            u ->
                                new InferenceServiceClient.InferContextSpec(
                                    u.contextId(),
                                    resolver.engineForContext(schemaId, u.contextId()),
                                    grouped
                                        .linesByContext()
                                        .getOrDefault(u.contextId(), List.of())))
                        .toList();
                Map<String, List<String>> derived =
                    inferenceClient.inferBatch(schemaId, inferSpecs);
                for (var u : batch) {
                  addDerivedModuleMetadata(quadSink, quadCount, u.graphUris());
                  if (leafIds.contains(u.contextId())) {
                    addCoverage(quadSink, quadCount, u.contextId(), u.finalContexts());
                  }
                  for (String triple : derived.getOrDefault(u.contextId(), List.of())) {
                    for (String graphUri : u.graphUris()) {
                      quadSink.accept(toModuleQuad(triple, graphUri + INF_SUFFIX));
                      quadCount.incrementAndGet();
                    }
                  }
                }
              }
            } catch (RuntimeException e) {
              errors.add(e);
            } finally {
              pending.decrementAndGet();
              done.countDown();
            }
          });
    }
    // Each reasoning batch runs two sequential RPCs (grouped graph read + inference), so budget
    // two timeout units per batch.
    awaitFanOut(done, pending, batches.size() * 2);
    throwFirstError(errors);
    return new FanOutResult(quadCount.get(), cacheHits.get(), cacheMisses.get());
  }

  private FanOutResult fanOutBatched(
      String schemaId,
      Map<String, Set<Context>> knowledgeMap,
      ParsedQuery parsed,
      Consumer<String> quadSink,
      AtomicLong quadCount,
      AtomicInteger cacheHits,
      AtomicInteger cacheMisses,
      Set<Throwable> errors) {
    // Membership is client-side per context and independent of the graph RPC.
    List<GraphQueryServiceClient.ContextQuerySpec> specs = new ArrayList<>(knowledgeMap.size());
    for (var entry : knowledgeMap.entrySet()) {
      Set<Context> finalContexts = entry.getValue();
      List<String> graphUris = finalContexts.stream().map(this::graphUriFor).toList();
      addContextMembership(quadSink, quadCount, graphUris, finalContexts, parsed.representation());
      specs.add(new GraphQueryServiceClient.ContextQuerySpec(entry.getKey(), graphUris));
    }

    List<List<GraphQueryServiceClient.ContextQuerySpec>> batches = new ArrayList<>();
    for (int i = 0; i < specs.size(); i += batchSize) {
      batches.add(specs.subList(i, Math.min(i + batchSize, specs.size())));
    }
    var pending = new AtomicInteger(batches.size());
    var done = new CountDownLatch(batches.size());
    for (var batch : batches) {
      executor.submit(
          () -> {
            try {
              var result =
                  graphClient.queryQuadsBatch(
                      schemaId,
                      batch,
                      parsed.representation(),
                      q -> {
                        quadSink.accept(q);
                        quadCount.incrementAndGet();
                      });
              cacheHits.addAndGet(result.cacheHits());
              cacheMisses.addAndGet(result.cacheMisses());
            } catch (RuntimeException e) {
              errors.add(e);
            } finally {
              pending.decrementAndGet();
              done.countDown();
            }
          });
    }
    awaitFanOut(done, pending, batches.size());
    throwFirstError(errors);
    return new FanOutResult(quadCount.get(), cacheHits.get(), cacheMisses.get());
  }

  private void awaitFanOut(CountDownLatch done, AtomicInteger pending, int units) {
    try {
      boolean finished =
          done.await(fanoutTimeoutSeconds * units + GLOBAL_TIMEOUT_PADDING_S, TimeUnit.SECONDS);
      if (!finished) {
        throw new QueryTimeoutException(
            "Fan-out timed out with " + pending.get() + " units pending");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new QueryTimeoutException("Fan-out interrupted");
    }
  }

  private void throwFirstError(Set<Throwable> errors) {
    if (!errors.isEmpty()) {
      Throwable first = errors.iterator().next();
      if (first instanceof RuntimeException re) throw re;
      throw new RuntimeException(first);
    }
  }

  /** Wraps an N-Triples line {@code S P O .} into the N-Quad {@code S P O <moduleGraph> .}. */
  private static String toModuleQuad(String nTriple, String moduleGraphUri) {
    String body = nTriple.stripTrailing();
    if (body.endsWith(".")) {
      body = body.substring(0, body.length() - 1).stripTrailing();
    }
    return body + " <" + moduleGraphUri + "> .";
  }

  private String graphUriFor(Context ctx) {
    return graphUriForId(ctx.id());
  }

  private String graphUriForId(String contextId) {
    return graphBaseUri + "context/" + contextId;
  }

  /**
   * Emits the derived-module metadata for each final context graph G: {@code G ckr:hasModule
   * G-inf}, {@code G-inf ckr:closureOf G}, {@code G-inf ckr:derivedFrom G-mod}. Only emitted when
   * reasoning is on (the base/asserted output declares only its {@code hasAssertedModule}).
   */
  private void addDerivedModuleMetadata(
      Consumer<String> sink, AtomicLong count, List<String> graphUris) {
    for (String graphUri : graphUris) {
      String g = "<" + graphUri + ">";
      String mod = "<" + graphUri + MOD_SUFFIX + ">";
      String inf = "<" + graphUri + INF_SUFFIX + ">";
      sink.accept(g + " <" + HAS_MODULE + "> " + inf + " " + g + " .");
      sink.accept(inf + " <" + CLOSURE_OF + "> " + g + " " + g + " .");
      sink.accept(inf + " <" + DERIVED_FROM + "> " + mod + " " + g + " .");
      count.addAndGet(3);
    }
  }

  /**
   * Emits the coverage hierarchy {@code Cg olap:covers Ci}: each final (general) context graph
   * covers the stored leaf context it was rolled up from. Skips self-coverage (no-merge queries).
   */
  private void addCoverage(
      Consumer<String> sink, AtomicLong count, String leafContextId, Set<Context> finalContexts) {
    String leafGraph = "<" + graphUriForId(leafContextId) + ">";
    for (Context fc : finalContexts) {
      if (fc.id().equals(leafContextId)) {
        continue;
      }
      String generalGraph = "<" + graphUriFor(fc) + ">";
      sink.accept(generalGraph + " <" + COVERS + "> " + leafGraph + " " + generalGraph + " .");
      count.incrementAndGet();
    }
  }

  private void addContextMembership(
      Consumer<String> sink,
      AtomicLong count,
      List<String> graphUris,
      Set<Context> finalContexts,
      GraphRepresentation representation) {
    Consumer<String> emit =
        s -> {
          sink.accept(s);
          count.incrementAndGet();
        };
    List<Context> contextList = new ArrayList<>(finalContexts);
    switch (representation) {
      case RDF -> emitMembershipQuads(emit, graphUris, contextList);
      case LPG -> emitMembershipElements(emit, graphUris, contextList);
      case GRAPH_FRAME -> emitMembershipRows(emit, graphUris, contextList);
    }
  }

  private void emitMembershipQuads(
      Consumer<String> emit, List<String> graphUris, List<Context> finalContexts) {
    int size = Math.min(graphUris.size(), finalContexts.size());
    for (int i = 0; i < size; i++) {
      String graphUri = graphUris.get(i);
      Context ctx = finalContexts.get(i);
      String graphNode = "<" + graphUri + ">";
      for (var dimEntry : ctx.hierarchies().entrySet()) {
        String dim = dimEntry.getKey();
        for (var member : dimEntry.getValue().members()) {
          String predicate = "<" + graphBaseUri + dim + "_" + member.level().name() + ">";
          String literal = "\"" + escape(member.value()) + "\"";
          emit.accept(graphNode + " " + predicate + " " + literal + " " + graphNode + " .");
        }
      }
      emitModuleMetadata(emit, graphUri);
    }
  }

  /**
   * Declares the asserted module of a context graph G: {@code G ckr:hasAssertedModule G-mod}.
   * Always emitted — the base facts live in the {@code -mod} module. The derived module ({@code
   * G-inf}) is declared separately, only when reasoning is on (see {@link
   * #addDerivedModuleMetadata}).
   */
  private void emitModuleMetadata(Consumer<String> emit, String graphUri) {
    String g = "<" + graphUri + ">";
    String mod = "<" + graphUri + MOD_SUFFIX + ">";
    emit.accept(g + " <" + HAS_ASSERTED_MODULE + "> " + mod + " " + g + " .");
  }

  private void emitMembershipElements(
      Consumer<String> emit, List<String> graphUris, List<Context> finalContexts) {
    int size = Math.min(graphUris.size(), finalContexts.size());
    for (int i = 0; i < size; i++) {
      String graphUri = graphUris.get(i);
      Context ctx = finalContexts.get(i);
      var props = membershipProps(ctx);
      var sb = new StringBuilder("{\"id\":\"" + graphUri + "\",\"label\":\"Context\",\"props\":{");
      boolean first = true;
      for (var e : props.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append("\"")
            .append(e.getKey())
            .append("\":\"")
            .append(escape(e.getValue()))
            .append("\"");
      }
      sb.append("}}");
      emit.accept(sb.toString());
    }
  }

  private void emitMembershipRows(
      Consumer<String> emit, List<String> graphUris, List<Context> finalContexts) {
    int size = Math.min(graphUris.size(), finalContexts.size());
    for (int i = 0; i < size; i++) {
      String graphUri = graphUris.get(i);
      Context ctx = finalContexts.get(i);
      var props = membershipProps(ctx);
      var sb =
          new StringBuilder(
              "{\"_kind\":\"v\",\"row\":{\"id\":\""
                  + graphUri
                  + "\",\"label\":\"Context\",\"props\":{");
      boolean first = true;
      for (var e : props.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append("\"")
            .append(e.getKey())
            .append("\":[\"")
            .append(escape(e.getValue()))
            .append("\"]");
      }
      sb.append("}}}");
      emit.accept(sb.toString());
    }
  }

  private Map<String, String> membershipProps(Context ctx) {
    var props = new LinkedHashMap<String, String>();
    for (var dimEntry : ctx.hierarchies().entrySet()) {
      String dim = dimEntry.getKey();
      for (var member : dimEntry.getValue().members()) {
        props.put(dim + "_" + member.level().name(), member.value());
      }
    }
    return props;
  }

  private String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }
}
