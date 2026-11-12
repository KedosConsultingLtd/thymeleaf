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
package org.thymeleaf.spring6.expression;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.StaticApplicationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * GHSA-f79q-9862-cq4c (fixed in 3.1.7): a blocked type allowed only through allowedClassOverridesForViews in one
 * evaluation context must stay blocked in other contexts (no overrides, or standalone) that evaluate the same
 * cached expression afterwards, including once the SpEL compiler has compiled that expression. The warm-up runs
 * enough renders for MIXED-mode compilation to happen (earlier Kedos tests warmed up only 3 times, so the compiled
 * path was never actually exercised).
 */
public class ClassOverridesAcrossContextsTest {

    private static final int WARM_UP_RENDERS = 200;

    private static StaticApplicationContext applicationContext;


    @BeforeAll
    public static void createApplicationContext() {
        applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
    }


    @ParameterizedTest(name = "compiler={0} {1}")
    @CsvSource(delimiter = '|', value = {
            "false|${x['name']}", "false|${x.name}", "false|${x.getName()}",
            "true|${x['name']}", "true|${x.name}", "true|${x.getName()}" })
    public void overridesDoNotLeakIntoOtherContexts(final boolean compiler, final String expression) {
        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        engine.setEnableSpringELCompiler(compiler);
        final String template = "<p th:text=\"" + expression + "\"></p>";

        assertForbidden(engine, template, withoutOverrides());
        for (int i = 0; i < WARM_UP_RENDERS; i++) {
            Assertions.assertEquals("<p>" + Thread.currentThread().getName() + "</p>",
                    render(engine, template, withThreadOverride()));
        }
        assertForbidden(engine, template, withoutOverrides());
        assertForbidden(engine, template, null);
    }


    private static ThymeleafEvaluationContext withoutOverrides() {
        return new ThymeleafEvaluationContext(applicationContext, null);
    }

    private static ThymeleafEvaluationContext withThreadOverride() {
        return new ThymeleafEvaluationContext(applicationContext, null, Collections.<Class<?>>singletonList(Thread.class));
    }

    private static void assertForbidden(
            final SpringTemplateEngine engine, final String template, final ThymeleafEvaluationContext evaluationContext) {
        Assertions.assertThrows(TemplateProcessingException.class, () -> render(engine, template, evaluationContext),
                "evaluation context " + (evaluationContext == null ? "standalone" : "without overrides"));
    }

    private static String render(
            final SpringTemplateEngine engine, final String template, final ThymeleafEvaluationContext evaluationContext) {
        final Context context = new Context();
        context.setVariable("x", Thread.currentThread());
        if (evaluationContext != null) {
            context.setVariable(
                    ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME, evaluationContext);
        }
        return engine.process(template, context);
    }

}
