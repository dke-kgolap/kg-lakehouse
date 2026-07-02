# engine-api

Service Provider Interface (SPI) for KG Lakehouse engines.

An "engine" turns a particular file format (AIXM, IWXXM, CSV, ...) into two
things the lakehouse needs: a set of `Hierarchy` objects (so the file can be
indexed by context) and a stream of triples (so the file can be reconstructed
into a graph at query time).

## Implementing an engine

Add a Gradle subproject (e.g. `engines/foo-engine`), depend on `engine-api`,
and implement four pieces:

```kotlin
class FooEngine : Engine {
    override val id = "foo"
    override val supportedMediaTypes = setOf("application/x-foo")
    override fun analyzer(): Analyzer = FooAnalyzer()
    override fun mapper(): Mapper = FooMapper()
}

class FooAnalyzer : Analyzer {
    override fun analyze(input: InputStream, schema: CubeSchema): AnalyzerResult { ... }
}

class FooMapper : Mapper {
    override fun map(input: InputStream, builder: GraphBuilder) { ... }
}
```

Then register the engine via `META-INF/services` so `ServiceLoader` discovers
it at runtime:

```
src/main/resources/META-INF/services/at.jku.dke.bigkgolap.engine.Engine
```

containing one fully-qualified class name per line:

```
com.example.foo.FooEngine
```

The framework calls `Engines.discover()` (or `Engines.find(mediaType)` /
`Engines.byId(id)`) at service startup; no other registration is required.

## Call order

1. **Ingestion path** (Surface → Ingestion Service):
   - `engine.analyzer().analyze(fileBytes, schema)` produces an
     `AnalyzerResult`. The `hierarchies` are upserted into the Cassandra
     index; the file itself is stored in MinIO.
2. **Query path** (Query Service → Graph Service):
   - For each context being reconstructed, the Graph Service loads the raw
     bytes from MinIO and calls `engine.mapper().map(fileBytes, builder)`.
     The builder accumulates triples; the Graph Service serialises and
     returns them.

## Contracts engines must honour

- **Analyzer is schema-aware, Mapper is not.** `Analyzer.analyze` is given
  the active `CubeSchema` so it can construct `Hierarchy`/`Member` objects
  whose levels match the schema's dimensions. `Mapper.map` produces the same
  triples regardless of schema — schema-driven filtering is the service
  layer's job.
- **Engines are schema-agnostic at the type level.** Don't gate on
  `schema.id`. If a file produces no relevant hierarchies for the active
  schema, return `AnalyzerResult.EMPTY`; the service layer will treat that
  as a no-op.
- **Inputs are owned by the caller.** Read but do not close the
  `InputStream` passed to `analyze` / `map`.
- **Mappers do not call `builder.build()`.** The Graph Service owns the
  builder lifecycle.
- **Thread-safety is opt-in.** `analyzer()` and `mapper()` are factories;
  if your engine returns shared instances, document that they're safe to
  call from multiple threads. The default assumption is "not".
