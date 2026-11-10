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
package org.thymeleaf.spring6.util;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.BeanReference;
import org.springframework.expression.spel.ast.ConstructorReference;
import org.springframework.expression.spel.ast.TypeReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Restricted-mode detection of instantiation / static access / bean and param access must ignore text inside SpEL
 * string literals (3.1.6 lower-cases expressions, so literals like 'New promotion' were rejected), without ever
 * missing real external access outside literals.
 */
public class SpringExternalAccessLiteralTest {

    @Test
    public void textInsideStringLiteralsIsIgnored() {
        final String[] safe = {
                "{{'Home','/admin'}, {'Promotions','/admin/catalogue/promotions'}, {'New promotion', null}}",
                "'New promotion'", "\"New promotion\"", "'NEW item'", "'Search params'", "'param'",
                "'me@example.com'", "'T(java.lang.Runtime)'", "'see t(1)'", "'it''s a new thing'",
                "\"say \"\"new\"\" things\"", "item.name + ' (new)'", "{'a', \"new b\"}", "''", "\"\"",
                "a && b", "a&&b", "a &&b", "flag && 'x@y'",
        };
        for (final String expression : safe) {
            Assertions.assertFalse(SpringStandardExpressionUtils.containsExternalAccess(expression), expression);
        }
    }

    @Test
    public void externalAccessOutsideLiteralsIsStillDetected() {
        final String[] unsafe = {
                "new java.lang.Thread()", "NEW java.lang.Thread()", "New java.util.ArrayList()",
                "T(java.lang.Runtime).getRuntime()", "t(java.lang.Runtime).getRuntime()", "@bean.x", "param.foo",
                " param['a']", "'a' + new java.util.Date()", "'it''s' + new java.util.Date()",
                "\"x\" + T(java.lang.Math).abs(1)", "'New promotion' + NEW java.util.Date()",
                "'abc\\' + new java.util.Date() + '", // no backslash escapes in SpEL: the literal ends after \
                "'unterminated new java.util.Date()", // unterminated literal: still scanned
                "{'a', new java.util.Date()}", "'x'.concat(T(java.lang.Math).toString())",
                "@'my bean'.x", "@\"myBean\"", "'a' + @'b'", // quoted bean references (missed upstream)
                "@ myBean.x", "@\tmyBean", "&myFactory", "& 'myFactory'", "a && &myFactory", "@\u00e9t\u00e9", // whitespace, factory, non-ASCII
                "new.java.lang.ProcessBuilder('id')", "new\u0000java.lang.ProcessBuilder('id')", "NEW\u0001java.util.Date()",
        };
        for (final String expression : unsafe) {
            Assertions.assertTrue(SpringStandardExpressionUtils.containsExternalAccess(expression), expression);
        }
    }

    @Test
    public void blankingKeepsQuotesAndLength() {
        Assertions.assertEquals("'xxxxxxxxxxxxx'", SpringStandardExpressionUtils.blankStringLiterals("'New promotion'"));
        Assertions.assertEquals("'xxxxxxxxxxx' + \"xxxx\"", SpringStandardExpressionUtils.blankStringLiterals("'it''s a new' + \"a\"\"b\""));
        Assertions.assertEquals("'abc new", SpringStandardExpressionUtils.blankStringLiterals("'abc new"));
        Assertions.assertEquals("no literals", SpringStandardExpressionUtils.blankStringLiterals("no literals"));
    }


    /*
     * Differential check against SpEL's own parser: for many generated expressions mixing quotes, escaped quotes,
     * backslashes, control characters, whitespace and keywords in different cases, whenever SpEL parses the
     * expression into a constructor call, type reference or bean reference, the detector must report external
     * access. ('param' detection is covered by the explicit cases above: upstream's rule only matches 'param'
     * followed by a non-identifier character, unchanged here.)
     */
    @Test
    public void neverMissesExternalAccessThatSpelWouldParse() {
        final String[] pieces = {
                "'", "\"", "''", "\"\"", "'a'", "\"b\"", "new ", "NEW ", "New ", "nEw ", "T(", "t(", ")",
                "java.util.ArrayList()", "java.lang.Math", ".abs(1)", "@", "bean", "param", "PARAM", ".x", "['k']",
                " + ", " ", "\t", "\u0001", "\u0085", "\\", "x", "{", "}", ",", "(", "promotion",
                "&", "&&", " and ", "@ ", "\u00e9", "$", "_b",
                "new", "\u0000", ".", "\u007f",
        };
        final SpelExpressionParser parser = new SpelExpressionParser();
        final Random random = new Random(1117L);
        int parsed = 0;
        int external = 0;
        for (int i = 0; i < 200_000; i++) {
            final StringBuilder expression = new StringBuilder();
            for (int p = 0, n = 1 + random.nextInt(9); p < n; p++) {
                expression.append(pieces[random.nextInt(pieces.length)]);
            }
            final String exp = expression.toString();
            final SpelNode ast;
            try {
                ast = ((SpelExpression) parser.parseExpression(exp)).getAST();
            } catch (final RuntimeException e) {
                continue; // SpEL refuses to parse it (ParseException or other errors): nothing can execute
            }
            parsed++;
            if (hasExternalAccess(ast)) {
                external++;
                Assertions.assertTrue(SpringStandardExpressionUtils.containsExternalAccess(exp),
                        "SpEL parses external access the detector missed: " + exp.replace("\u0001", "\\u0001"));
            }
        }
        Assertions.assertTrue(parsed > 10_000 && external > 1_000, "parsed=" + parsed + " external=" + external);
    }

    private static boolean hasExternalAccess(final SpelNode node) {
        if (node instanceof ConstructorReference || node instanceof TypeReference || node instanceof BeanReference) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (hasExternalAccess(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }


    /*
     * End to end: fragment parameters are evaluated in restricted mode. Breadcrumb literals containing "New ..."
     * must work again; real instantiation in a fragment parameter must still be rejected.
     */
    @Test
    public void fragmentParametersAcceptLiteralsButNotInstantiation() {
        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        final String fragment = "<h1 th:fragment=\"hdr(crumbs)\" th:text=\"${crumbs == null ? 0 : #lists.size(crumbs)}\">x</h1>";
        Assertions.assertEquals("<h1>3</h1><h1>0</h1>", engine.process(
                "<div th:replace=\"~{::hdr(${ {{'Home','/admin'}, {'Promotions','/admin/catalogue/promotions'}, {'New promotion', null}} })}\"></div>"
                        + fragment, new Context()));
        for (final String forbidden : new String[] { "new java.util.ArrayList()", "NEW java.util.ArrayList()",
                "{'New promotion', new java.util.Date()}" }) {
            Assertions.assertThrows(TemplateProcessingException.class, () -> engine.process(
                    "<div th:replace=\"~{::hdr(${" + forbidden + "})}\"></div>" + fragment, new Context()), forbidden);
        }
    }

}
