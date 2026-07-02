# IWXXM Rulesets

Forward-chaining RDFS / OWL-RL rules for the lakehouse meteo cube populated
from **IWXXM** sources. These rules express the per-cell instance-level
closure that should hold inside every cube cell once a TBox is bound.

These rules are **IWXXM-specific**.

## Files

| File | Variant |
| --- | --- |
| `iwxxm-ruleset.rules` | Default: six rules covering the OWL 2 RL fragment. |
| `iwxxm-ruleset-domain_range.rules` | Default + `rdfs:domain` and `rdfs:range` propagation. **Recommended starting point for IWXXM** — see *TBox notes for IWXXM* below. |

## Cube-schema mapping

Each `ckr:Context` in the dataset describes a single cube cell, addressed by
three coordinate properties:

| Property | Cube dimension | Levels (coarse → fine) |
| --- | --- | --- |
| `cube:hasTime` | `time` | year → month → day |
| `cube:hasLocation` | `location` | territory → fir → location |
| `cube:hasTopic` | `topic` | category → family → feature |

IWXXM ingests into the shared **`meteo`** cube schema (not a dedicated
`iwxxm.yaml`). The schema lives at `config/schemas/meteo.yaml`. Today its
topic hierarchy is:

| category | family | feature |
| --- | --- | --- |
| `Meteorological` | `Weather` | `METAR` |
| `Meteorological` | `Weather` | `TAF` |
| `Meteorological` | `Weather` | `SIGMET` |

The shared CKR / OLAP URIs are:

- `cube:` = `http://dke.jku.at/ckr/cube-model#`
- `olap:` = `http://dke.jku.at/ckr/olap-model#`
- `ckr:`  = `http://dke.jku.at/ckr/meta#`

These URIs **do not appear in the rule files**. The rules operate on a single
graph holding one cell's asserted triples plus the relevant TBox.

## Orchestration in the lakehouse

Identical to the AIXM and FIXM rulesets' orchestration — the same query and
inference services serve IWXXM cells. The cell-level orchestration around
the rules lives outside this folder:

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
in `inference-service`'s `ReasoningConfig`). `IwxxmEngine` declares
`ontologyResource() = "iwxxm-tbox.ttl"` and `rulesResource() =
"iwxxm-ruleset-domain_range.rules"`; both are bundled into the
`iwxxm-engine` jar at build time via the pom's Maven `<resources>` block
pointing at `../../rulesets/iwxxm/`. Derived triples
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

## TBox notes for IWXXM

**Mixed IWXXM + AIXM namespaces.** Unlike FIXM, IWXXM features routinely
embed AIXM features — e.g. an `iwxxm:METAR` carries its `iwxxm:aerodrome`
as an `aixm:AirportHeliport`. The engine
(`engines/iwxxm-engine/.../IwxxmRdfWriter.java:51-67`) recognises both
`iwxxm:` and `aixm:` prefixes and emits typed triples for each. A
supplementary TBox covering IWXXM data may therefore want to reuse AIXM
class axioms for the aerodrome/FIR features.

**ICAO/WMO codelists are available as RDF/SKOS.** Unlike FIXM, IWXXM has
real semantic-web tooling: the WMO Codes Registry at <http://codes.wmo.int>
publishes IWXXM's controlled vocabularies (aerodrome categories, cloud
amounts, weather phenomena, etc.) as a Linked Data Registry with RDF/SKOS
representations. ICAO-managed registers (aerodrome, runway, FIR) similarly
offer RDF/SKOS alongside HTML and XML. **Caveat:** SKOS uses
`skos:broader` / `skos:narrower` for concept hierarchies, **not**
`rdfs:subClassOf` — these rules will *not* traverse a WMO codelist
hierarchy without bridging axioms (e.g. asserting that each `skos:Concept`
is also a class and that `skos:broader` aligns with `rdfs:subClassOf`).
Whether to do that bridging, or to reason over codelists separately with a
SKOS-specific ruleset, is a design choice still open.

**What the IWXXM engine emits**
(`engines/iwxxm-engine/.../IwxxmRdfWriter.java`):

- `?subject rdf:type <BASE_URI + METAR>` (and `TAF`, `SIGMET`, plus
  embedded `AirportHeliport`, `Airspace`, etc. from AIXM) — typing
  triples for each IWXXM/AIXM feature, with `rdf:type` as an IRI so
  RDFS reasoning can fire.
- `?parent <BASE_URI + propertyName> ?child` — object properties from
  XML nesting (e.g. `aerodrome`, `validPeriod`, `affectedFIR`).
- `?subject <BASE_URI + propertyName> "literal"` — datatype properties
  from leaf text.

**Which rules actually fire today.** The auto-derived TBox (built by
`SchemaTBoxBuilder` from the cube's topic hierarchy) provides only
`rdfs:subClassOf` chains — for IWXXM that is the three feature ⊑ Weather
⊑ Meteorological chains. So today **only rule 1 (`subClassOf`) fires**;
rules 2–8 are valid but dormant until a richer TBox is supplied.

**Why `+domain_range` is the more useful variant for IWXXM.** IWXXM, like
FIXM, is property-heavy: every weather observation field
(`issueTime`, `validPeriod`, `aerodrome`, `affectedFIR`, …) is emitted as
a typed property. `rdfs:domain` / `rdfs:range` axioms on those properties
are the most natural IWXXM ontology pattern, and rules 7 and 8 are what
will earn their keep once such axioms are added.
