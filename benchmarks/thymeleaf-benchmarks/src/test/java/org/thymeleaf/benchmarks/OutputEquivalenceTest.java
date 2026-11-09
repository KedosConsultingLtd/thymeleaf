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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.thymeleaf.benchmarks.engine.EngineKind;
import org.thymeleaf.benchmarks.engine.Variant;

import java.util.stream.Stream;

/**
 * Every benchmark combination must render, and every optimised variant must reproduce its reference output exactly.
 */
public class OutputEquivalenceTest {

    static Stream<Arguments> combinations() {
        final Stream.Builder<Arguments> builder = Stream.builder();
        for (final EngineKind engine : EngineKind.values()) {
            for (final Scenario scenario : Scenario.values()) {
                for (final Variant variant : Variant.values()) {
                    builder.add(Arguments.of(engine, scenario, variant));
                }
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("combinations")
    public void rendersAndMatchesReference(final EngineKind engine, final Scenario scenario, final Variant variant) {
        final String output = new BenchmarkFixture(engine, variant, scenario).renderToString();
        Assertions.assertTrue(output.length() > 1000, "suspiciously small output");
        Assertions.assertFalse(output.contains("??"), "unresolved message key");
        BenchmarkFixture.verifyOutput(engine, variant, scenario);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("combinations")
    public void engineKindsRenderTheSamePage(final EngineKind ignored, final Scenario scenario, final Variant variant) {
        // OGNL and SpEL engines must produce identical pages, so their timings are comparable
        Assertions.assertEquals(
                new BenchmarkFixture(EngineKind.STANDARD, variant, scenario).renderToString(),
                new BenchmarkFixture(EngineKind.SPRING, variant, scenario).renderToString());
    }

}
