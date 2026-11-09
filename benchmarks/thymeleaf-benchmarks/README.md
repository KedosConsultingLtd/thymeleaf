# Thymeleaf rendering benchmarks

JMH benchmarks that render complete, realistic HTML pages and report:
- the latency distribution of a single render (p50 to p99.99);
- bytes allocated per render;
- the size of the cached template model.

Generic: no application code or private templates. Intended to be upstreamable alongside the parse-filter work.

This is a standalone Maven module, **not** part of the root reactor, so it never affects released artifacts.

## What is measured

Each invocation renders one full page from a warm template cache into a reusable UTF-8 byte sink (`Utf8Sink`). The
sink stands in for a servlet response: the cost of character encoding is included, I/O is not.

| Parameter | Values |
|---|---|
| `engine` | `STANDARD`: core `TemplateEngine`, OGNL expressions, map-backed message resolver. `SPRING`: `SpringTemplateEngine`, SpEL expressions, messages from a Spring `MessageSource` |
| `scenario` | `HOME`: hero, department tiles, 8 product cards. `LISTING`: facets, sort, 48 product cards, pagination. `PRODUCT`: gallery, options form, accordion, 12 reviews, 4 related cards. `CONTENT`: long, mostly static article |
| `variant` | `BASELINE` (stock engine), `WHITESPACE` (parse-time whitespace collapsing), `COALESCING` (parse-time static-run coalescing), `WHITESPACE_AND_COALESCING` |

All pages share a head, a header with a 240-link mega-menu (plus a mobile menu), and a static-heavy footer, as
typical server-rendered storefronts do. Templates are in `src/main/resources/templates/bench/`. The data is
generated with a fixed seed (`Catalogue`), so every run renders exactly the same pages.

**Output is checked before anything is measured.** Each trial's setup fails if a variant does not reproduce its
reference output byte for byte: `COALESCING` must equal `BASELINE`, and `WHITESPACE_AND_COALESCING` must equal
`WHITESPACE`. `mvn test` checks every combination, and also checks that the OGNL and SpEL engines render identical
pages.

## Build

The module depends on the Thymeleaf version in your local Maven repository, so install the libraries first:

```bash
# from the repository root
(cd lib/thymeleaf && mvn install -DskipTests)
(cd lib/thymeleaf-spring6 && mvn install -DskipTests)

cd benchmarks/thymeleaf-benchmarks
mvn package            # runs the output-equivalence tests, builds target/benchmarks.jar
```

## Run

```bash
java -jar target/benchmarks.jar RenderBenchmark                          # full matrix, ~10 min
java -jar target/benchmarks.jar RenderBenchmark -prof gc                 # + bytes allocated per render
java -jar target/benchmarks.jar RenderBenchmark -p scenario=LISTING -p engine=SPRING
java -jar target/benchmarks.jar RenderBenchmark -bm thrpt                # throughput instead of latency
java -jar target/benchmarks.jar RenderBenchmark -t 4                     # 4 concurrent rendering threads
java -jar target/benchmarks.jar RenderBenchmark -rf json -rff out.json   # machine-readable results

# Cached-model size and output size per page and variant (no timing):
java -cp target/benchmarks.jar org.thymeleaf.benchmarks.ModelStatsReport
```

Reading the results:
- `render` in sample-time mode gives the average and percentiles of one render, in microseconds.
- `render:gc.alloc.rate.norm` (with `-prof gc`) is the bytes allocated per render, which drives GC frequency.
- Compare variants against each other on the same machine and run. The absolute numbers depend heavily on the
  hardware.
- A single-thread p99 here is not a server's p99 under load. It shows how much work and variance one render carries.

## Layout

| Class | Role |
|---|---|
| `RenderBenchmark` | The JMH benchmark (parameters, setup with output check, one render per invocation) |
| `BenchmarkFixture` | One configured engine plus the context for one scenario; the output equivalence check |
| `Scenario` | The pages and the variables each one receives |
| `engine.EngineKind` / `engine.Variant` | How each engine is built, and which optional dialects each variant registers |
| `engine.Utf8Sink` | Allocation-free UTF-8 output sink |
| `model.*` | Plain data classes and the deterministic `Catalogue` generator |
| `ModelStatsReport` | Prints cached event counts and output sizes |

## Adding scenarios

1. Add a template under `src/main/resources/templates/bench/` and any messages to `messages.properties`.
2. Add a `Scenario` constant that names the template and builds its variables.
3. Run `mvn test`. The new page is then checked across all engines and variants.

TODO: support an external template directory plus a serialised model per page, so application templates (with
their own dialects) can be benchmarked without adding them to this module.
