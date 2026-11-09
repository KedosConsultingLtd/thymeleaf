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

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.StaticApplicationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.stream.Stream;

/**
 * The SpEL property-accessor cache must never let a property read skip the ACL, nor change which accessor reads a
 * property. Each case reuses the same expression node across targets of different types (in one render and across
 * renders), which is exactly when SpEL serves the read from its cache.
 */
public class ACLPropertyAccessorCachingTest {

    private static final String NAME_LOOP = "<p th:each=\"x : ${items}\" th:text=\"${x.name}\">x</p>";
    private static final String EMPTY_LOOP = "<p th:each=\"x : ${items}\" th:text=\"${x.empty}\">x</p>";


    public static final class Named {
        private final String name;
        public Named(final String name) { this.name = name; }
        public String getName() { return this.name; }
        public String getEmpty() { return "bean"; }
    }


    static Stream<Arguments> configurations() {
        return Stream.of(
                Arguments.of(false, false), Arguments.of(false, true),
                Arguments.of(true, false), Arguments.of(true, true));
    }


    @ParameterizedTest(name = "compiler={0} thymeleafEvaluationContext={1}")
    @MethodSource("configurations")
    public void forbiddenTypeAfterAllowedTypeInSameRender(final boolean compiler, final boolean evaluationContext) {
        final SpringTemplateEngine engine = engine(compiler);
        assertForbidden(engine, NAME_LOOP, Arrays.asList(new Named("ok"), new File("secret")), evaluationContext);
        assertForbidden(engine, NAME_LOOP, Arrays.asList(new File("secret"), new Named("ok")), evaluationContext);
    }


    @ParameterizedTest(name = "compiler={0} thymeleafEvaluationContext={1}")
    @MethodSource("configurations")
    public void forbiddenTypeAfterAllowedTypeAcrossRenders(final boolean compiler, final boolean evaluationContext) {
        final SpringTemplateEngine engine = engine(compiler);
        for (int i = 0; i < 3; i++) { // Warm the expression cache and SpEL's accessor cache on the allowed type
            Assertions.assertEquals("<p>ok</p>", render(engine, NAME_LOOP, Arrays.asList(new Named("ok")), evaluationContext));
        }
        assertForbidden(engine, NAME_LOOP, Arrays.asList(new File("secret")), evaluationContext);
        Assertions.assertEquals("<p>ok</p>", render(engine, NAME_LOOP, Arrays.asList(new Named("ok")), evaluationContext));
    }


    @ParameterizedTest(name = "compiler={0} thymeleafEvaluationContext={1}")
    @MethodSource("configurations")
    public void mapKeysStillTakePrecedenceOverGetters(final boolean compiler, final boolean evaluationContext) {
        final SpringTemplateEngine engine = engine(compiler);
        final Map<String, Object> withKey = new HashMap<>();
        withKey.put("empty", "map");
        final Map<String, Object> withoutKey = new HashMap<>();
        withoutKey.put("other", "x");
        final List<Object> items = Arrays.asList(new Named("a"), withKey, withoutKey, new Named("b"), withKey);
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(
                    "<p>bean</p><p>map</p><p>false</p><p>bean</p><p>map</p>",
                    render(engine, EMPTY_LOOP, items, evaluationContext));
        }
    }


    /** How the SpEL evaluation context is provided for a render. */
    enum ContextKind { STANDALONE, THYMELEAF, THYMELEAF_WITH_OVERRIDES, CUSTOM_DELEGATE }

    private static final String INDEXER = "<p th:text=\"${p['name']}\">x</p><p th:text=\"${p[field]}\">x</p>";


    @ParameterizedTest(name = "compiler={0}")
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    public void bracketAccessWorksAcrossAllContextKindsInAnyOrder(final boolean compiler) {
        final SpringTemplateEngine engine = engine(compiler);
        final ContextKind[] sequence = {
                ContextKind.STANDALONE, ContextKind.THYMELEAF, ContextKind.THYMELEAF_WITH_OVERRIDES,
                ContextKind.THYMELEAF_WITH_OVERRIDES, ContextKind.CUSTOM_DELEGATE, ContextKind.CUSTOM_DELEGATE,
                ContextKind.STANDALONE, ContextKind.THYMELEAF_WITH_OVERRIDES, ContextKind.THYMELEAF };
        for (final ContextKind kind : sequence) {
            Assertions.assertEquals("<p>ok</p><p>ok</p>", renderBracket(engine, new Named("ok"), kind), kind.name());
        }
    }


    @ParameterizedTest(name = "compiler={0}")
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    public void bracketAccessToForbiddenTypeIsStillRejected(final boolean compiler) {
        final SpringTemplateEngine engine = engine(compiler);
        for (final ContextKind kind : ContextKind.values()) {
            Assertions.assertEquals("<p>ok</p><p>ok</p>", renderBracket(engine, new Named("ok"), kind));
            final TemplateProcessingException e = Assertions.assertThrows(
                    TemplateProcessingException.class, () -> renderBracket(engine, new File("secret"), kind), kind.name());
            assertForbiddenCause(e);
        }
    }


    @org.junit.jupiter.api.Test
    public void concurrentRendersWithMixedContextsNeverFail() throws Exception {
        final SpringTemplateEngine engine = engine(false);
        final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        final java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        final List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < 8; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 300; i++) {
                    final ContextKind kind = ContextKind.values()[(thread + i) % ContextKind.values().length];
                    try {
                        if (!"<p>ok</p><p>ok</p>".equals(renderBracket(engine, new Named("ok"), kind))
                                || !"<p>ok</p>".equals(render(engine, NAME_LOOP, Arrays.asList(new Named("ok")), kind))) {
                            failures.incrementAndGet();
                        }
                    } catch (final RuntimeException e) {
                        failures.incrementAndGet();
                    }
                }
            }));
        }
        for (final java.util.concurrent.Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        Assertions.assertEquals(0, failures.get());
    }


    private static String renderBracket(final SpringTemplateEngine engine, final Object target, final ContextKind kind) {
        final Context context = new Context();
        context.setVariable("p", target);
        context.setVariable("field", "name");
        applyContextKind(context, kind);
        return engine.process(INDEXER, context);
    }

    private static String render(final SpringTemplateEngine engine, final String template, final List<?> items, final ContextKind kind) {
        final Context context = new Context();
        context.setVariable("items", items);
        applyContextKind(context, kind);
        return engine.process(template, context);
    }

    private static void applyContextKind(final Context context, final ContextKind kind) {
        final String name = ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME;
        switch (kind) {
            case THYMELEAF:
                context.setVariable(name, new ThymeleafEvaluationContext(new StaticApplicationContext(), null));
                break;
            case THYMELEAF_WITH_OVERRIDES: // New ACL accessor instances for every request
                context.setVariable(name, new ThymeleafEvaluationContext(
                        new StaticApplicationContext(), null, java.util.Collections.<Class<?>>singleton(java.util.UUID.class)));
                break;
            case CUSTOM_DELEGATE: // Wrapped by the evaluator in a new ThymeleafEvaluationContextWrapper every request
                context.setVariable(name, new org.springframework.expression.spel.support.StandardEvaluationContext());
                break;
            default:
                break;
        }
    }

    private static void assertForbiddenCause(final Throwable e) {
        Throwable cause = e;
        while (cause != null && (cause.getMessage() == null || !cause.getMessage().contains("forbidden"))) {
            cause = cause.getCause();
        }
        Assertions.assertNotNull(cause, "expected an ACL 'forbidden' error, got: " + e);
    }


    private static SpringTemplateEngine engine(final boolean compiler) {
        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        engine.setEnableSpringELCompiler(compiler);
        return engine;
    }

    private static String render(
            final SpringTemplateEngine engine, final String template, final List<?> items, final boolean evaluationContext) {
        final Context context = new Context();
        context.setVariable("items", items);
        if (evaluationContext) {
            context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                    new ThymeleafEvaluationContext(new StaticApplicationContext(), null));
        }
        return engine.process(template, context);
    }

    private static void assertForbidden(
            final SpringTemplateEngine engine, final String template, final List<?> items, final boolean evaluationContext) {
        final TemplateProcessingException e = Assertions.assertThrows(
                TemplateProcessingException.class, () -> render(engine, template, items, evaluationContext));
        Throwable cause = e;
        while (cause != null && (cause.getMessage() == null || !cause.getMessage().contains("forbidden"))) {
            cause = cause.getCause();
        }
        Assertions.assertNotNull(cause, "expected an ACL 'forbidden' error, got: " + e);
    }

}
