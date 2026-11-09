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
package org.thymeleaf.linkbuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ExpressionContext;
import org.thymeleaf.context.IExpressionContext;

/**
 * The optimised {@link StandardLinkBuilder} must produce exactly what the original 3.1.6 implementation
 * ({@link ReferenceStandardLinkBuilder}) produces, including the same exceptions, for every combination of link
 * base, parameters and context path below. It must also never modify the parameter map it is given.
 */
public class StandardLinkBuilderEquivalenceTest {

    private static final List<String> BASES = Arrays.asList(
            "", "/", "/a", "a", "/a/{id}", "/a/{id}/b/{id}", "/a/{/seg}", "/a/{/seg}/{seg}", "~/p/{slug}",
            "~/p/{slug}?x=1", "~/", "~/p#top", "/p?q={q}", "/p/{id}?q={q}#frag", "/p/{id}?q={id}", "#frag", "#{id}",
            "/a#{id}", "http://x.example/{id}/z", "HTTP://x.example/p", "//cdn.example/{f}", "mailto:{who}",
            "rel/{a}", "{a}", "{a}{b}", "/{a}{a}", "/x/{a}?{a}", "/a/{missing}", "/a/{id", "/a/id}", "/{}", "/{/}",
            "/{{id}}", "/{ id }", "/café/{id}", "javascript:alert(1)", " JavaScript:x", "java\tscript:x",
            "{a}", "~/{a}", "/a?b", "/a?", "/a/{id}?", "/a/{id}#", "/x/{a b}", "/x/{}");

    private static final List<Map<String, Object>> PARAMETERS = parameterSets();

    private static final List<String> CONTEXT_PATHS = Arrays.asList(null, "", "/", "/shop");


    @Test
    public void sameOutputAsReferenceImplementation() {
        final IExpressionContext context = new ExpressionContext(new TemplateEngine().getConfiguration());
        int compared = 0;
        for (final String contextPath : CONTEXT_PATHS) {
            final StandardLinkBuilder optimised = new FixedContextPathLinkBuilder(contextPath);
            final ReferenceStandardLinkBuilder reference = new FixedContextPathReferenceLinkBuilder(contextPath);
            for (final String base : BASES) {
                for (final Map<String, Object> parameters : PARAMETERS) {
                    final Map<String, Object> before = (parameters == null ? null : new LinkedHashMap<>(parameters));
                    final String expected = outcome(() -> reference.buildLink(context, base, copy(parameters)));
                    final String actual = outcome(() -> optimised.buildLink(context, base, parameters));
                    Assertions.assertEquals(expected, actual,
                            "base=" + base + " parameters=" + parameters + " contextPath=" + contextPath);
                    Assertions.assertEquals(before, parameters, "parameters must not be modified");
                    compared++;
                }
            }
        }
        Assertions.assertTrue(compared > 3000);
    }


    @Test
    public void indexOfTemplateMatchesStringSearch() {
        final List<String> sequences = Arrays.asList("", "{", "{a}", "x{a}y{a}", "{/a}{a}", "{ab}{a}", "{a", "a}", "{{a}}", "{/}", "{}");
        for (final String sequence : sequences) {
            for (final String name : Arrays.asList("", "a", "ab", "/a")) {
                for (final boolean segment : new boolean[] { false, true }) {
                    final String template = (segment ? "{/" : "{") + name + "}";
                    for (int from = 0; from <= sequence.length() + 1; from++) {
                        Assertions.assertEquals(
                                sequence.indexOf(template, from),
                                StandardLinkBuilder.indexOfTemplate(new StringBuilder(sequence), name, segment, from),
                                "sequence=" + sequence + " template=" + template + " from=" + from);
                    }
                }
            }
        }
    }


    private static String outcome(final Action action) {
        try {
            return "OK:" + action.run();
        } catch (final Exception e) {
            return "EXCEPTION:" + e.getClass().getName() + ":" + e.getMessage();
        }
    }

    private interface Action {
        String run();
    }

    private static Map<String, Object> copy(final Map<String, Object> parameters) {
        return (parameters == null ? null : new LinkedHashMap<>(parameters));
    }


    private static List<Map<String, Object>> parameterSets() {
        final List<Map<String, Object>> sets = new ArrayList<>();
        sets.add(null);
        sets.add(Collections.<String, Object>emptyMap());
        sets.add(params("id", 5));
        sets.add(params("id", "a b/c?d#e"));
        sets.add(params("slug", "x?y&z"));
        sets.add(params("seg", "a/b"));
        sets.add(params("q", "x&y=z"));
        sets.add(params("a", null));
        sets.add(params("a", Arrays.asList(1, 2, null)));
        sets.add(params("a", Collections.emptyList()));
        sets.add(params("extra", "v", "id", "1"));
        sets.add(params("id", "1", "extra", Arrays.asList("x", "y")));
        sets.add(params("a b", "c", "id", "é"));
        sets.add(params("", "x"));
        sets.add(params("id", "{id}"));
        sets.add(params("a", "{b}", "b", "Z"));
        sets.add(params("b", "Z", "a", "{b}"));
        sets.add(params("a", "javascript:alert(1)"));
        sets.add(params("a", " javascript:alert(1)", "b", "x"));
        sets.add(params("who", "me@example.com", "subject", "hi there"));
        sets.add(params("id", "1", "seg", "s", "q", "q", "missing", "m", "f", "f.js", "slug", "s", "who", "w", "h", "h"));
        return sets;
    }

    private static Map<String, Object> params(final Object... keyValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(map); // Fails the test if the builder tries to modify it
    }


    private static final class FixedContextPathLinkBuilder extends StandardLinkBuilder {
        private final String contextPath;
        FixedContextPathLinkBuilder(final String contextPath) {
            this.contextPath = contextPath;
        }
        @Override
        protected String computeContextPath(final IExpressionContext context, final String base, final Map<String, Object> parameters) {
            return this.contextPath;
        }
    }

    private static final class FixedContextPathReferenceLinkBuilder extends ReferenceStandardLinkBuilder {
        private final String contextPath;
        FixedContextPathReferenceLinkBuilder(final String contextPath) {
            this.contextPath = contextPath;
        }
        @Override
        protected String computeContextPath(final IExpressionContext context, final String base, final Map<String, Object> parameters) {
            return this.contextPath;
        }
    }

}
