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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.BeanReference;
import org.springframework.expression.spel.ast.ConstructorReference;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.StringLiteral;
import org.springframework.expression.spel.ast.TypeReference;
import org.springframework.expression.spel.ast.VariableReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.thymeleaf.spring6.util.SpringStandardExpressionUtils;

/**
 * Differential test of the restricted-mode external access check for SpEL, exactly as
 * SPELVariableExpressionEvaluator applies it (text pre-check, then confirmation on the parsed expression without
 * string literal contents), against SpEL's own parser.
 * <ul>
 *   <li><b>Miss</b>: SpEL parses a constructor, type or bean reference, or a {@code param} property, variable or
 *       method, and the check lets it through.</li>
 *   <li><b>Literal false positive</b> (#1118): the check rejects an expression with none of those, and it would
 *       accept the same expression with its string literal contents blanked.</li>
 * </ul>
 * Inputs: random concatenations of tokens, quotes, keywords in mixed case, control and Unicode characters and
 * escape-like text, plus every BMP character in every slot of the dangerous token forms. A longer run (millions of
 * expressions) is available by raising {@code -Dkedos.fuzz.expressions}.
 */
public class SPELRestrictedModeGateDifferentialTest {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private static final String[] PIECES = {
            "'", "\"", "''", "\"\"", "'a'", "\"b\"", "'new x'", "'T(x)'", "'@b'", "'param'", "\"&b\"",
            "new ", "NEW ", "nEw", "new", "new.", "T(", "t(", "T ", "(", ")", "[", "]", "{", "}", ",", ".", "?.", "?[",
            "![", "^[", "$[", "java.util.ArrayList()", "java.lang.Math", ".abs(1)", "Date", "()",
            "@", "&", "&&", "||", " and ", " or ", "!", "?", ":", "?:", " instanceof ", " matches ", " between ", "==",
            "+", "-", "*", "bean", "param", "PARAM", "Param", "#param", "#root", "#this", "#vars", "#ctx", ".param",
            "['param']", "x", "_b", "$", "$b", "1", "1.5e3", "0x1F",
            " ", "\t", "\n", "\r", "\f", "\u000b", "\u0000", "\u0001", "\u001f", "\u007f", "\u0085", "\u00a0",
            "\u2028", "\u3000", "\ufeff", "\u00e9", "\u00aa", "\u0130", "\u0131", "\u017f", "\u212a",
            "\\", "\\u0040", "\\u006e", "\\'", "/*", "*/", "//", "#" };

    private static final String[] SLOT_TEMPLATES = {
            "%snew java.util.Date()", "n%sw java.util.Date()", "new%sjava.util.Date()", "ne%s java.util.Date()",
            "%sT(java.lang.Math).abs(1)", "%s(java.lang.Math).abs(1)", "T%s(java.lang.Math).abs(1)",
            "x+T%s(java.lang.Math)", "@%sbean", "%sbean.x", "&%sbean", "a&%s&bean", "pa%sram", "par%sam.x",
            "%sparam", "param%s", "x.%sparam", "#%sparam", "'a'%snew java.util.Date()", "new%s'a'",
            "{'a'%snew java.util.Date()}", "'%s' + new java.util.Date()", "\"%s\" + T(java.lang.Math)",
            "x[%s'k'] + new java.util.Date()", "'k'%s'k' + @b" };


    @Test
    public void checkMatchesSpelParser() {
        final Result result = new Result();
        final Random random = new Random(1117L);
        final long expressions = Long.getLong("kedos.fuzz.expressions", 200_000L);
        for (long i = 0; i < expressions; i++) {
            final StringBuilder expression = new StringBuilder();
            for (int p = 0, n = 1 + random.nextInt(14); p < n; p++) {
                expression.append(PIECES[random.nextInt(PIECES.length)]);
            }
            result.check(expression.toString());
        }
        for (final String template : SLOT_TEMPLATES) {
            for (int c = 0; c <= 0xFFFF; c++) {
                result.check(String.format(template, (char) c));
            }
        }
        Assertions.assertTrue(result.misses.isEmpty(), "Missed external access: " + result.misses);
        Assertions.assertTrue(result.literalFalsePositives.isEmpty(),
                "Rejected only because of string literal contents: " + result.literalFalsePositives);
        Assertions.assertTrue(result.parsed > 300_000 && result.dangerous > 100_000,
                "parsed=" + result.parsed + " dangerous=" + result.dangerous);
    }


    private static final class Result {

        final Map<String, String> misses = new TreeMap<>();
        final Map<String, String> literalFalsePositives = new TreeMap<>();
        long parsed;
        long dangerous;

        void check(final String expression) {
            final SpelNode ast;
            try {
                ast = ((SpelExpression) PARSER.parseExpression(expression)).getAST();
            } catch (final RuntimeException e) {
                return; // SpEL refuses it: nothing can execute
            }
            this.parsed++;
            final boolean rejected = isRejected(expression);
            final String danger = findExternalAccess(ast);
            if (danger != null) {
                this.dangerous++;
                if (!rejected && this.misses.size() < 20) {
                    this.misses.put(printable(expression), danger);
                }
            } else if (rejected && this.literalFalsePositives.size() < 20) {
                final List<SpelNode> literals = new ArrayList<>();
                collectStringLiterals(ast, literals);
                final String blanked = blankLiterals(expression, literals);
                if (!literals.isEmpty() && !SpringStandardExpressionUtils.containsExternalAccess(blanked)) {
                    this.literalFalsePositives.put(printable(expression), printable(blanked));
                }
            }
        }

    }


    private static boolean isRejected(final String expression) {
        return SpringStandardExpressionUtils.containsExternalAccess(expression)
                && SPELExpressionRestrictions.containsExternalAccessOutsideStringLiterals(PARSER, expression);
    }


    private static String findExternalAccess(final SpelNode node) {
        if (node instanceof ConstructorReference) {
            return "constructor";
        }
        if (node instanceof TypeReference) {
            return "type";
        }
        if (node instanceof BeanReference) {
            return "bean";
        }
        if (node instanceof PropertyOrFieldReference && ((PropertyOrFieldReference) node).getName().equalsIgnoreCase("param")) {
            return "param property";
        }
        if (node instanceof VariableReference && node.toStringAST().equalsIgnoreCase("#param")) {
            return "param variable";
        }
        if (node instanceof MethodReference && ((MethodReference) node).getName().equalsIgnoreCase("param")) {
            return "param method";
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final String danger = findExternalAccess(node.getChild(i));
            if (danger != null) {
                return danger;
            }
        }
        return null;
    }


    private static void collectStringLiterals(final SpelNode node, final List<SpelNode> literals) {
        if (node.getClass() == StringLiteral.class) {
            literals.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectStringLiterals(node.getChild(i), literals);
        }
    }


    private static String blankLiterals(final String expression, final List<SpelNode> literals) {
        final char[] chars = expression.toCharArray();
        for (final SpelNode literal : literals) {
            for (int i = literal.getStartPosition() + 1; i < literal.getEndPosition() - 1 && i < chars.length; i++) {
                chars[i] = 'x';
            }
        }
        return new String(chars);
    }


    private static String printable(final String text) {
        final StringBuilder printable = new StringBuilder();
        for (final char c : text.toCharArray()) {
            printable.append(c < 0x20 || c > 0x7e ? String.format("\\u%04x", (int) c) : String.valueOf(c));
        }
        return printable.toString();
    }

}
