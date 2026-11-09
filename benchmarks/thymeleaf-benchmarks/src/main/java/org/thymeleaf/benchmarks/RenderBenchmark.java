/*
 * =============================================================================
 *
 *   Copyright (c) 2026 Kedos Consulting Limited
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 * =============================================================================
 */
package org.thymeleaf.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.thymeleaf.benchmarks.engine.EngineKind;
import org.thymeleaf.benchmarks.engine.Utf8Sink;
import org.thymeleaf.benchmarks.engine.Variant;

/**
 * <p>
 *   Renders one full page per invocation, from a warm template cache, into a UTF-8 byte sink.
 * </p>
 * <p>
 *   Default mode is sample time, which reports the latency distribution (p50 … p99.99) of a single render.
 *   Add {@code -prof gc} for bytes allocated per render, {@code -bm thrpt} for throughput and {@code -t N} for
 *   concurrent renders. See README.md.
 * </p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = { "-Xms1g", "-Xmx1g" })
public class RenderBenchmark {

    @Param({ "STANDARD", "SPRING" })
    public EngineKind engine;

    @Param({ "HOME", "LISTING", "PRODUCT", "CONTENT" })
    public Scenario scenario;

    @Param({ "BASELINE", "WHITESPACE", "COALESCING", "WHITESPACE_AND_COALESCING" })
    public Variant variant;

    private BenchmarkFixture fixture;


    @State(Scope.Thread)
    public static class Sink {
        final Utf8Sink sink = new Utf8Sink();
    }


    @Setup(Level.Trial)
    public void setUp() {
        BenchmarkFixture.verifyOutput(this.engine, this.variant, this.scenario);
        this.fixture = new BenchmarkFixture(this.engine, this.variant, this.scenario);
        this.fixture.renderToString(); // Fill the template and expression caches
    }


    @Benchmark
    public int render(final Sink sink) {
        sink.sink.reset();
        this.fixture.render(sink.sink);
        return sink.sink.size();
    }

}
