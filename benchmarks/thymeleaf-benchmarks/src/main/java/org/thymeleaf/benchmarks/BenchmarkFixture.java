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

import java.nio.charset.StandardCharsets;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.benchmarks.engine.EngineKind;
import org.thymeleaf.benchmarks.engine.Utf8Sink;
import org.thymeleaf.benchmarks.engine.Variant;
import org.thymeleaf.benchmarks.model.Catalogue;
import org.thymeleaf.context.Context;

/**
 * One configured engine plus the context for one scenario: everything a benchmark invocation needs.
 */
public final class BenchmarkFixture {

    private static final Catalogue CATALOGUE = Catalogue.generate(64);

    private final TemplateEngine engine;
    private final Scenario scenario;
    private final Context context;


    public BenchmarkFixture(final EngineKind engineKind, final Variant variant, final Scenario scenario) {
        this.engine = engineKind.create(variant);
        this.scenario = scenario;
        this.context = new Context(EngineKind.LOCALE, scenario.variables(CATALOGUE));
    }


    public TemplateEngine getEngine() {
        return this.engine;
    }

    public void render(final Utf8Sink sink) {
        this.engine.process(this.scenario.getTemplate(), this.context, sink);
    }

    public String renderToString() {
        final Utf8Sink sink = new Utf8Sink();
        render(sink);
        return new String(sink.toByteArray(), StandardCharsets.UTF_8);
    }


    /**
     * Fails if this fixture's variant does not reproduce its reference variant's output exactly, so that no
     * benchmark ever measures a broken optimisation.
     */
    public static void verifyOutput(final EngineKind engineKind, final Variant variant, final Scenario scenario) {
        final Variant reference = variant.referenceOutput();
        if (reference == variant) {
            return;
        }
        final String expected = new BenchmarkFixture(engineKind, reference, scenario).renderToString();
        final String actual = new BenchmarkFixture(engineKind, variant, scenario).renderToString();
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Output of " + engineKind + "/" + variant + "/" + scenario + " differs from " + reference
                    + " (first difference at char " + firstDifference(expected, actual) + ")");
        }
    }

    private static int firstDifference(final String a, final String b) {
        final int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return n;
    }

}
