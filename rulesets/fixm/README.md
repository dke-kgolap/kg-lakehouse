# FIXM Rulesets

Forward-chaining RDFS / OWL-RL rules for the lakehouse ATM cube populated from
**FIXM** sources. These rules express the per-cell instance-level closure that
should hold inside every cube cell once a TBox is bound.

These rules are **FIXM-specific**.

## Files

| File | Variant |
| --- | --- |
| `fixm-ruleset.rules` | Default: six rules covering the OWL 2 RL fragment. |
| `fixm-ruleset-domain_range.rules` | Default + `rdfs:domain` and `rdfs:range` propagation. **Recommended starting point for FIXM** — see *TBox notes for FIXM* below. |

## Cube-schema mapping

Each `ckr:Context` in the dataset describes a single cube cell, addressed by
three coordinate properties:

| Property | Cube dimension | Levels (coarse → fine) |
| --- | --- | --- |
| `cube:hasTime` | `time` | year → month → day |
| `cube:hasLocation` | `location` | territory → fir → location |
| `cube:hasTopic` | `topic` | category → family → feature |

The cube schema lives in `config/schemas/fixm.yaml`. Today its topic hierarchy
is:

| category | family | feature |
| --- | --- | --- |
| `Flights` | `Flight` | `Flight` |
| `Flights` | `Trajectory` | `RouteTrajectoryElement` |

(`RouteTrajectoryElement` is reserved for a future trajectory engine; today
the FIXM analyzer indexes every flight under `Flight`.)

The shared CKR / OLAP URIs are:

- `cube:` = `http://dke.jku.at/ckr/cube-model#`
- `olap:` = `http://dke.jku.at/ckr/olap-model#`
- `ckr:`  = `http://dke.jku.at/ckr/meta#`

These URIs **do not appear in the rule files**. The rules operate on a single
graph holding one cell's asserted triples plus the relevant TBox.

## Orchestration in the lakehouse

Identical to the AIXM ruleset's orchestration — the same query and inference
services serve FIXM cells. The cell-level orchestration around the rules
lives outside this folder:

**Selecting which cells participate in a query.**
`libs/index-client/.../CassandraIndexRepository.java:365` (`intersectContextIds`)
is invoked once per query, from
`services/query-service/.../ContextResolverService.java`. For each query
predicate the orchestrator reads the `(schema, dimension)` partition of the
`hierarchies` Cassandra table, filters rows whose hierarchy members match the
predicate, and **set-intersects** the `context_ids` columns of the surviving
rows. The intersection is the set of stored leaf contexts whose three
dimension coordinates all match — the candidate cells.

**Materializing per-cell graphs.**
`services/inference-service/.../ContextInferenceService.java:59` (`infer`)
handles one context at a time. It locates the files backing that context (via
the `files` Cassandra table), parses the base quads into a single Jena
`Model`, assembles the TBox via `TBoxRegistry.tboxFor(schema, engine)`, and
delegates closure to `InferenceEngine.infer(engine, base, tbox, profile)`
(`libs/reasoning/.../GenericRuleInferenceEngine.java`, the `@Primary` bean
in `inference-service`'s `ReasoningConfig`). `FixmEngine` declares
`ontologyResource() = "fixm-tbox.ttl"` and `rulesResource() =
"fixm-ruleset-domain_range.rules"`; both are bundled into the
`fixm-engine` jar at build time via the pom's Maven `<resources>` block
pointing at `../../rulesets/fixm/`. Derived triples
are cached per `(schema, context, tbox-version)` in `DerivedCache` so that an
unchanged context is not re-reasoned on the next query.

**Propagating inferences across coarser/finer cells.**
`services/query-service/.../MergeAndPropagateService.java:14`
(`mergeAndPropagate`) walks the candidate cells: for each stored leaf, it
finds every other candidate whose coordinates **roll up to** the leaf (via
`Context.rollsUpTo(schema)`), then optionally rolls each one up to a
query-specified merge level. The resulting coverage relation is emitted on
the wire by `QueryOrchestrator.addCoverage`
(`services/query-service/.../QueryOrchestrator.java:349`) as `olap:covers`
quads, alongside the CKR module metadata (`ckr:hasModule`, `ckr:closureOf`,
`ckr:derivedFrom`) produced by `addDerivedModuleMetadata` (line 332). The
CKR vocabulary itself lives at
`libs/graph-builders/.../CkrVocabulary.java`.

## Rule catalogue

| # | Rule | TBox construct consumed | What it derives |
| - | --- | --- | --- |
| 1 | `subClassOf` | `rdfs:subClassOf` | `?x rdf:type ?z` from `?y ⊑ ?z` + `?x rdf:type ?y` |
| 2 | `subPropertyOf` | `rdfs:subPropertyOf` | `?x ?w ?x1` from `?v ⊑ ?w` + `?x ?v ?x1` |
| 3 | `propertyChainAxiom2` | `owl:propertyChainAxiom` (length 2) | `?x ?w ?z` from `?w ≡ ?u ∘ ?v` + matching triples |
| 4 | `intersectionOf2` | `owl:intersectionOf` (binary) under `rdfs:subClassOf` | `?x rdf:type ?z` from `?y1 ⊓ ?y2 ⊑ ?z` + both type assertions |
| 5 | `someValuesFrom` | `owl:someValuesFrom` + `owl:onProperty` + `rdfs:subClassOf` | `?x rdf:type ?z` from `∃?v.?y ⊑ ?z` + matching property/type |
| 6 | `hasValue` | `owl:hasValue` + `owl:onProperty` + `rdfs:subClassOf` | `?x ?r ?x1` from `?y ⊑ {∃?r.{?x1}}` + `?x rdf:type ?y` |
| 7 *(variant)* | `rdfsDomain` | `rdfs:domain` | `?s rdf:type ?d` from `?p domain ?d` + `?s ?p ?o` |
| 8 *(variant)* | `rdfsRange` | `rdfs:range` | `?o rdf:type ?r` from `?p range ?r` + `?s ?p ?o` |

## OWL-RL gaps

These constructs are **not** reasoned over:

- `owl:allValuesFrom` — universal restriction.
- `owl:equivalentClass` / `owl:equivalentProperty` — must be pre-expanded into two `rdfs:subClassOf` / `rdfs:subPropertyOf` triples.
- `owl:sameAs`, `owl:differentFrom`.
- `owl:disjointWith`, cardinality restrictions, functional / inverse-functional properties.
- Datatype reasoning.
- `owl:intersectionOf` with arity greater than 2 (the rule is hardcoded to a two-element list; longer intersections must be pre-flattened).

## TBox notes for FIXM

**No public FIXM OWL ontology exists.** FIXM's reference model is UML +
XSD; OGC Testbed-14 explicitly notes that "there is currently no encoding
based on linked data standards such as the Web Ontology Language (OWL) or
RDF Schema." Any FIXM TBox the lakehouse reasons over is therefore either
auto-derived from the cube schema or hand-built.

**What the FIXM engine emits** (`engines/fixm-engine/.../FixmRdfWriter.java`):

- `?subject rdf:type <BASE_URI + Flight>` (and `RouteTrajectoryElement`,
  etc.) — typing triples for each FIXM feature, with `rdf:type` as an IRI
  so RDFS reasoning can fire.
- `?parent <BASE_URI + propertyName> ?child` — object properties from XML
  nesting (e.g. `departureAerodrome`, `arrival`).
- `?subject <BASE_URI + propertyName> "literal"` — datatype properties
  from leaf text.

**Which rules actually fire today.** The auto-derived TBox (built by
`SchemaTBoxBuilder` from the cube's topic hierarchy) provides only
`rdfs:subClassOf` chains — for FIXM that is `RouteTrajectoryElement ⊑
Trajectory ⊑ Flights` and `Flight ⊑ Flight ⊑ Flights`. So today **only
rule 1 (`subClassOf`) fires**; rules 2–8 are valid but dormant until a
richer TBox is supplied.

**Why `+domain_range` is the more useful variant for FIXM.** FIXM is
property-heavy: every flight attribute (`departureAerodrome`,
`arrivalAerodrome`, `estimatedOffBlockTime`, `routeTrajectoryElement`, …)
is emitted as a typed property. `rdfs:domain` / `rdfs:range` axioms on
those properties are the most natural FIXM ontology pattern, and rules 7
and 8 are what will earn their keep once such axioms are added. The
default variant is the strict OWL-RL fragment without that convenience.
