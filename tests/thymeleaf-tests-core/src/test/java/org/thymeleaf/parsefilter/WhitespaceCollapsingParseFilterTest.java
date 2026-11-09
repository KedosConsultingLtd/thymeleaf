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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;

public class WhitespaceCollapsingParseFilterTest {

    private static TemplateEngine engine(final MapTemplateResolver resolver, final boolean collapse) {
        final TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        if (collapse) {
            engine.addDialect(new WhitespaceCollapsingDialect());
        }
        return engine;
    }

    private static String render(final boolean cacheable, final String template) {
        return render(cacheable, template, new Context());
    }

    private static String render(final boolean cacheable, final String template, final Context context) {
        return engine(new MapTemplateResolver(cacheable).with("t", template), true).process("t", context);
    }

    private static void assertCollapsed(final String expected, final String template) {
        // Same result whether the template goes through the cache (model builder) or straight to processing
        Assertions.assertEquals(expected, render(true, template), "cacheable");
        Assertions.assertEquals(expected, render(false, template), "not cacheable");
    }


    @Test
    public void collapsesIndentationBetweenTags() {
        assertCollapsed("<div>\n<p>a</p>\n</div>\n", "<div>\n    <p>a</p>\n  </div>\n\n");
    }

    @Test
    public void collapsesSingleSpaceBetweenInlineElements() {
        assertCollapsed("<b>a</b>\n<i>b</i>", "<b>a</b> <i>b</i>");
    }

    @Test
    public void leavesMixedTextUntouched() {
        assertCollapsed("<p>  hello\n   world  </p>", "<p>  hello\n   world  </p>");
    }

    @Test
    public void leavesNonBreakingSpaceUntouched() {
        assertCollapsed("<p>\u00A0</p><p>&nbsp;</p>", "<p>\u00A0</p><p>&nbsp;</p>");
    }

    @ParameterizedTest
    @ValueSource(strings = { "pre", "textarea", "script", "style", "PRE" })
    public void preservesWhitespaceInsidePreservingElements(final String element) {
        final String inner = "\n   <b>x</b>\n   ";
        final String template = "<div>\n  <" + element + ">" + inner + "</" + element + ">\n  <p>y</p>\n</div>";
        // Note: inside script/style/textarea the parser treats content as text, inside pre as markup:
        // either way it must come out unchanged.
        final String expected = "<div>\n<" + element + ">" + inner + "</" + element + ">\n<p>y</p>\n</div>";
        assertCollapsed(expected, template);
    }

    @Test
    public void preservesWhitespaceInElementsNestedInPre() {
        assertCollapsed("<pre><b>   </b>\n  <i> </i></pre>\n<p>z</p>", "<pre><b>   </b>\n  <i> </i></pre>   <p>z</p>");
    }

    @Test
    public void unmatchedClosingTagDoesNotDisablePreservation() {
        assertCollapsed("<pre>\n  </b>  </pre>\n<p>z</p>", "<pre>\n  </b>  </pre>   <p>z</p>");
    }

    @Test
    public void collapsesAroundCommentsButKeepsCommentContent() {
        assertCollapsed("<div>\n<!--  keep   me  -->\n</div>", "<div>\n  <!--  keep   me  -->\n  </div>");
    }

    @Test
    public void doesNotCollapseProcessingOutput() {
        final Context context = new Context();
        context.setVariable("ws", "   \n   ");
        Assertions.assertEquals(
                "<div>\n<span>   \n   </span>\n</div>",
                render(true, "<div>\n  <span th:text=\"${ws}\">x</span>\n</div>", context));
    }

    @Test
    public void collapsesWhitespaceRepeatedByIteration() {
        final Context context = new Context();
        context.setVariable("items", Arrays.asList("a", "b"));
        Assertions.assertEquals(
                "<ul>\n<li>a</li>\n<li>b</li>\n</ul>",
                render(true, "<ul>\n    <li th:each=\"i : ${items}\" th:text=\"${i}\">x</li>\n  </ul>", context));
    }

    @Test
    public void collapsesInsertedFragments() {
        final MapTemplateResolver resolver = new MapTemplateResolver(true)
                .with("page", "<main>\n  <div th:insert=\"~{frags :: f}\">x</div>\n</main>")
                .with("frags", "<html>\n  <section th:fragment=\"f\">\n      <h1>t</h1>\n  </section>\n</html>");
        Assertions.assertEquals(
                "<main>\n<div><section>\n<h1>t</h1>\n</section></div>\n</main>",
                engine(resolver, true).process("page", new Context()));
    }

    @Test
    public void fragmentSelectedAsScriptElementIsPreserved() {
        final MapTemplateResolver resolver = new MapTemplateResolver(true)
                .with("page", "<main>\n  <th:block th:replace=\"~{frags :: s}\"></th:block>\n</main>")
                .with("frags", "<html>\n  <script th:fragment=\"s\">\n    var a = 1;\n  </script>\n</html>");
        Assertions.assertEquals(
                "<main>\n<script>\n    var a = 1;\n  </script>\n</main>",
                engine(resolver, true).process("page", new Context()));
    }

    @Test
    public void withoutDialectOutputIsUnchanged() {
        final String template = "<div>\n    <p>a</p>\n  </div>";
        Assertions.assertEquals(
                template, engine(new MapTemplateResolver(true).with("t", template), false).process("t", new Context()));
    }

    @Test
    public void textTemplateModeIsNotFiltered() {
        final String template = "line one\n\n     \n[# th:if=\"true\"]   [/]\nline two";
        final MapTemplateResolver resolver = new MapTemplateResolver(true, TemplateMode.TEXT).with("t", template);
        Assertions.assertEquals(
                engine(new MapTemplateResolver(true, TemplateMode.TEXT).with("t", template), false).process("t", new Context()),
                engine(resolver, true).process("t", new Context()));
    }

    @Test
    public void cachedRenderIsStableAcrossRenders() {
        final TemplateEngine engine = engine(new MapTemplateResolver(true).with("t", "<a>\n  <b>c</b>\n</a>"), true);
        final String first = engine.process("t", new Context());
        final String second = engine.process("t", new Context());
        Assertions.assertEquals("<a>\n<b>c</b>\n</a>", first);
        Assertions.assertEquals(first, second);
    }

}
