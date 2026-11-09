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

import java.util.Locale;

import org.thymeleaf.benchmarks.engine.EngineKind;
import org.thymeleaf.benchmarks.engine.Variant;
import org.thymeleaf.cache.ICache;
import org.thymeleaf.cache.TemplateCacheKey;
import org.thymeleaf.engine.TemplateModel;

/**
 * Prints, for each scenario and variant, the number of events held in the template cache after one render
 * (all templates and fragments the page uses) and the size of the rendered page. Fewer cached events means less
 * work per render. Run with:
 * {@code java -cp target/benchmarks.jar org.thymeleaf.benchmarks.ModelStatsReport}
 */
public final class ModelStatsReport {

    private ModelStatsReport() {
    }

    public static void main(final String[] args) {
        System.out.println("| Scenario | Variant | Cached templates | Cached events | Output bytes |");
        System.out.println("|---|---|---|---|---|");
        for (final Scenario scenario : Scenario.values()) {
            for (final Variant variant : Variant.values()) {
                final BenchmarkFixture fixture = new BenchmarkFixture(EngineKind.STANDARD, variant, scenario);
                final int bytes = fixture.renderToString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                final ICache<TemplateCacheKey, TemplateModel> cache =
                        fixture.getEngine().getConfiguration().getCacheManager().getTemplateCache();
                int events = 0;
                for (final TemplateCacheKey key : cache.keySet()) {
                    events += cache.get(key).size();
                }
                System.out.println(String.format(Locale.ROOT, "| %s | %s | %d | %d | %d |",
                        scenario, variant, cache.keySet().size(), events, bytes));
            }
        }
    }

}
