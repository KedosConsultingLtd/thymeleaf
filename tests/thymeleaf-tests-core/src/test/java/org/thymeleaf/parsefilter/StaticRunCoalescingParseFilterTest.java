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
package org.thymeleaf.parsefilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.cache.ICache;
import org.thymeleaf.cache.TemplateCacheKey;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.engine.TemplateModel;

/**
 * Static-run coalescing must never change rendered output, only the number of events in the cached model.
 * Every case renders with and without coalescing (cached, uncached, and twice from the cache) and compares.
 */
public class StaticRunCoalescingParseFilterTest {

    private static final String MAIN = "main";


    // ---------------------------------------------------------------------------------------------------------
    // Output equivalence
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void fullyStaticPage() {
        assertSameOutput(page("<html>\n<head><title>t</title></head>\n<body>\n  <div class=\"a\"><p>x</p><br><img src=\"i.png\"/></div>\n</body>\n</html>"));
    }

    @Test
    public void expressionsInsideStaticMarkup() {
        assertSameOutput(page(
                "<div>\n  <header><nav><a href=\"/\">home</a></nav></header>\n"
                + "  <p th:text=\"${name}\">x</p>\n  <footer><small>(c)</small></footer>\n</div>"));
    }

    @Test
    public void iterationKeepsPrecedingWhitespace() {
        assertSameOutput(page(
                "<ul>\n  <li class=\"first\">static</li>\n  <li th:each=\"i : ${items}\" th:text=\"${i}\">x</li>\n"
                + "  <li class=\"last\"><b>static</b></li>\n</ul>\n<table>\n  <tr th:each=\"i : ${items}\"><td th:text=\"${i}\">x</td><td>static</td></tr>\n</table>"));
    }

    @Test
    public void removeAllButFirstKeepsChildElements() {
        assertSameOutput(page(
                "<ul th:remove=\"all-but-first\">\n  <li><b>one</b></li>\n  <li><b>two</b></li>\n  <li>three</li>\n</ul>"
                + "<div th:remove=\"${flag} ? all-but-first : none\">\n  <p>a</p>\n  <p>b</p>\n</div>"
                + "<div th:remove=\"body\"><p>gone</p></div><div th:remove=\"tag\"><p>kept</p></div>"
                + "<div><p th:remove=\"all\">gone</p><p>kept</p></div>"));
    }

    @Test
    public void conditionalsAndScopes() {
        assertSameOutput(page(
                "<div th:if=\"${flag}\"><p>yes</p><p>still yes</p></div><div th:unless=\"${flag}\"><p>no</p></div>"
                + "<div th:with=\"x=${name}\"><span>static</span><span th:text=\"${x}\">x</span></div>"
                + "<th:block th:each=\"i : ${items}\"><hr><span>static</span></th:block>"
                + "<div th:switch=\"${name}\"><p th:case=\"'World'\">w</p><p th:case=\"*\">other</p></div>"
                + "<div th:object=\"${person}\"><p>static</p><p th:text=\"*{name}\">x</p></div>"));
    }

    @Test
    public void textInlining() {
        assertSameOutput(page(
                "<div><p>Hello [[${name}]]</p><p>plain</p><p>[(${html})]</p></div>"
                + "<div th:inline=\"text\"><a title=\"[[${name}]]\">attribute is not inlined</a></div>"
                + "<div th:inline=\"none\"><p>[[${name}]]</p><p>static</p></div>"
                + "<p>not [ inline ] text</p>"));
    }

    @Test
    public void scriptAndStyleInlining() {
        assertSameOutput(page(
                "<script th:inline=\"javascript\">\n  var name = /*[[${name}]]*/ 'x';\n  var other = 1;\n</script>\n"
                + "<script>var plain = 'static';</script>\n"
                + "<script th:inline=\"javascript\">\n  var a = 1; /*[+ var b = [[${name}]]; +]*/\n</script>\n"
                + "<style th:inline=\"css\">.a { color: /*[[${colour}]]*/ red; }</style>\n<style>.b { color: blue; }</style>"));
    }

    @Test
    public void commentsAndCData() {
        assertSameOutput(page(
                "<div><!-- static comment --><p>x</p></div>\n<!--[if IE]><p th:text=\"${name}\">ie</p><![endif]-->\n"
                + "<div><!-- inline [[${name}]] --></div>\n<!--/* parser-level comment */-->\n<!--/*/ <p>parser block</p> /*/-->\n"
                + "<div><![CDATA[ static cdata ]]><p>after</p></div>"));
    }

    @Test
    public void doctypeAndUnmatchedTags() {
        assertSameOutput(page("<!DOCTYPE html>\n<html>\n<body>\n<div><p>x</p></span></div>\n</body>\n</html>"));
        assertSameOutput(page("</div><p>unmatched at root</p>"));
    }

    @Test
    public void fragmentInsertion() {
        final Map<String, String> templates = new LinkedHashMap<String, String>();
        templates.put(MAIN, "<main>\n  <div th:insert=\"~{frags :: f}\">x</div>\n  <div th:replace=\"~{frags :: f}\">x</div>\n"
                + "  <div th:include=\"~{frags :: f}\">x</div>\n  <div th:insert=\"~{frags :: #byid}\">x</div>\n"
                + "  <div th:replace=\"~{frags :: #byid}\">x</div>\n  <div th:include=\"~{frags :: #byid}\">x</div>\n"
                + "  <div th:insert=\"~{frags :: withParam(${name})}\">x</div>\n  <div th:insert=\"~{frags}\">x</div>\n</main>");
        templates.put("frags", "<html>\n<body>\n  <section th:fragment=\"f\">\n    <h1>title</h1>\n    <p>static <b>body</b></p>\n  </section>\n"
                + "  <aside id=\"byid\"><ul><li>a</li><li>b</li></ul></aside>\n"
                + "  <div th:fragment=\"withParam(v)\"><span>static</span><span th:text=\"${v}\">x</span></div>\n</body>\n</html>");
        assertSameOutput(templates);
    }

    @Test
    public void modelProcessorsSeeOriginalElementsWhenTheirDialectIsTrusted() {
        final Map<String, String> templates = page(
                "<div test:count=\"\">\n  <ul><li>a</li><li>b</li></ul>\n  <p>static <b>text</b></p>\n</div><footer><p>x</p><p>y</p></footer>");
        final String expected = render(templates, true, new CountChildElementsDialect());
        final IDialect trustedCoalescing = new StaticRunCoalescingDialect(
                Collections.<Class<? extends IDialect>>singleton(CountChildElementsDialect.class));
        Assertions.assertEquals(expected, render(templates, true, new CountChildElementsDialect(), trustedCoalescing));
        Assertions.assertEquals(expected, render(templates, false, new CountChildElementsDialect(), trustedCoalescing));
        Assertions.assertTrue(cachedEventCount(templates, new CountChildElementsDialect(), trustedCoalescing)
                < cachedEventCount(templates, new CountChildElementsDialect()));
    }

    @Test
    public void untrustedModelProcessorDialectDisablesCoalescing() {
        // e.g. a layout/decorator dialect: its model processors may walk markup of this or other templates
        final Map<String, String> templates = page("<div><p>a</p><p>b</p></div><span test:count=\"\"><i>x</i></span>");
        Assertions.assertEquals(
                cachedEventCount(templates, new CountChildElementsDialect()),
                cachedEventCount(templates, new CountChildElementsDialect(), new StaticRunCoalescingDialect()));
        assertSameOutput(templates, new CountChildElementsDialect());
    }

    @Test
    public void switchedModeInliningUnderAncestorIsNeverSkipped() {
        // Ancestor th:inline switches inlining mode at render time: element blocks and prototype-only comments
        // inside nested static elements must still be processed (authorisation-gated content must not leak)
        assertSameOutput(page(
                "<body th:inline=\"javascript\"><div><span><script>/*[- var secret = 'PROTOTYPE'; -]*/ var a = 1;"
                + " /*[+ var added = true; +]*/ [# th:if=\"${false}\"]var admin = true;[/] [# th:unless=\"${flag}\"]var guest = 1;[/]"
                + "</script></span><p>static</p></div></body>"
                + "<div th:inline=\"text\"><section><p>[# th:if=\"${false}\"]hidden[/] visible</p></section></div>"
                + "<div th:inline=\"css\"><section><style>/*[- .proto {} -]*/ .a { color: red; }</style></section></div>"
                + "<div><![CDATA[[${name}]]><p>after</p></div>"));
    }

    @Test
    public void runtimeStringParsesAreNotFiltered() {
        // IModelFactory.parse of model data must return exactly what the parser produced (no coalescing, no
        // whitespace collapsing), so sanitising dialects see every element
        final Map<String, String> templates = page("<div test:parse=\"html\">x</div>");
        final Context context = context();
        context.setVariable("html", "<p>Hi <b>x</b>   \n   <script>s()</script><img src=x onerror=e()></p>");
        final String expected = engine(templates, true, new ModelParseDialect()).process(MAIN, context);
        Assertions.assertTrue(expected.contains("OpenElementTag:<script>"), expected);
        Assertions.assertEquals(expected, engine(templates, true, new ModelParseDialect(),
                new WhitespaceCollapsingDialect(), new StaticRunCoalescingDialect(
                        Collections.<Class<? extends IDialect>>singleton(ModelParseDialect.class))).process(MAIN, context));
        // th:utext with a post-processor configured re-parses the value: whitespace in model data is kept
        final Map<String, String> utext = page("<pre th:utext=\"${html}\">x</pre>");
        Assertions.assertEquals(
                engine(utext, true, new PassThroughPostProcessorDialect()).process(MAIN, context),
                engine(utext, true, new PassThroughPostProcessorDialect(), new WhitespaceCollapsingDialect()).process(MAIN, context));
    }

    @Test
    public void deeplyNestedMarkupIsHandledWithoutStackOverflow() {
        for (final int depth : new int[] { 255, 257, 20000 }) {
            final StringBuilder template = new StringBuilder("<main>");
            for (int i = 0; i < depth; i++) {
                template.append("<div>");
            }
            template.append("<span th:text=\"${name}\">x</span>");
            for (int i = 0; i < depth; i++) {
                template.append("</div>");
            }
            template.append("<p>static</p></main>");
            assertSameOutput(page(template.toString()));
        }
    }

    @Test
    public void combinedWithWhitespaceCollapsing() {
        final Map<String, String> templates = page(
                "<html>\n  <body>\n    <ul>\n      <li th:each=\"i : ${items}\" th:text=\"${i}\">x</li>\n    </ul>\n    <pre>\n  keep\n    </pre>\n  </body>\n</html>");
        Assertions.assertEquals(
                render(templates, true, new WhitespaceCollapsingDialect()),
                render(templates, true, new WhitespaceCollapsingDialect(), new StaticRunCoalescingDialect()));
    }


    // ---------------------------------------------------------------------------------------------------------
    // Effect on the cached model
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void staticMarkupIsCoalescedInCachedModel() {
        final Map<String, String> templates = page(
                "<html>\n<body>\n  <div class=\"a\"><p>x</p><p>y</p></div>\n  <p th:text=\"${name}\">x</p>\n  <footer><p>f</p></footer>\n</body>\n</html>");
        final int baseline = cachedEventCount(templates);
        final int coalesced = cachedEventCount(templates, new StaticRunCoalescingDialect());
        // html (root) and body (has a dynamic descendant) stay elements; the rest folds around the th:text element
        Assertions.assertTrue(coalesced <= baseline / 2, "baseline=" + baseline + " coalesced=" + coalesced);
    }

    @Test
    public void postProcessorsDisableCoalescing() {
        final Map<String, String> templates = page("<div><p>a</p><p>b</p></div>");
        Assertions.assertEquals(
                cachedEventCount(templates, new PassThroughPostProcessorDialect()),
                cachedEventCount(templates, new PassThroughPostProcessorDialect(), new StaticRunCoalescingDialect()));
        assertSameOutput(templates, new PassThroughPostProcessorDialect());
    }


    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private static Map<String, String> page(final String content) {
        final Map<String, String> templates = new LinkedHashMap<String, String>();
        templates.put(MAIN, content);
        return templates;
    }

    private static Context context() {
        final Context context = new Context();
        context.setVariable("name", "World");
        context.setVariable("html", "<i>raw</i>");
        context.setVariable("colour", "green");
        context.setVariable("flag", Boolean.TRUE);
        context.setVariable("items", Arrays.asList("a", "b", "c"));
        final Map<String, Object> person = new LinkedHashMap<String, Object>();
        person.put("name", "Ada");
        context.setVariable("person", person);
        return context;
    }

    private static TemplateEngine engine(final Map<String, String> templates, final boolean cacheable, final IDialect... dialects) {
        final MapTemplateResolver resolver = new MapTemplateResolver(cacheable);
        for (final Map.Entry<String, String> entry : templates.entrySet()) {
            resolver.with(entry.getKey(), entry.getValue());
        }
        final TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        for (final IDialect dialect : dialects) {
            engine.addDialect(dialect);
        }
        return engine;
    }

    private static String render(final Map<String, String> templates, final boolean cacheable, final IDialect... dialects) {
        return engine(templates, cacheable, dialects).process(MAIN, context());
    }

    private static void assertSameOutput(final Map<String, String> templates, final IDialect... extraDialects) {
        final String expected = render(templates, true, extraDialects);
        final IDialect[] withCoalescing = Arrays.copyOf(extraDialects, extraDialects.length + 1);
        withCoalescing[extraDialects.length] = new StaticRunCoalescingDialect();

        Assertions.assertEquals(expected, render(templates, false, withCoalescing), "not cacheable");
        final TemplateEngine cached = engine(templates, true, withCoalescing);
        Assertions.assertEquals(expected, cached.process(MAIN, context()), "cacheable, first render");
        Assertions.assertEquals(expected, cached.process(MAIN, context()), "cacheable, from cache");
    }

    private static int cachedEventCount(final Map<String, String> templates, final IDialect... dialects) {
        final TemplateEngine engine = engine(templates, true, dialects);
        engine.process(MAIN, context());
        final ICache<TemplateCacheKey, TemplateModel> cache = engine.getConfiguration().getCacheManager().getTemplateCache();
        for (final TemplateCacheKey key : cache.keySet()) {
            if (MAIN.equals(key.getTemplate())) {
                return cache.get(key).size();
            }
        }
        throw new AssertionError("Template not found in cache");
    }

}
