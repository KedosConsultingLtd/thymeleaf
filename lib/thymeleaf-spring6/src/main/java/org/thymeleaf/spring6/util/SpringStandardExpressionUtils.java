/*
 * =============================================================================
 *
 *   Copyright (c) 2011-2026 Thymeleaf (http://www.thymeleaf.org)
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

import org.thymeleaf.util.ExpressionUtils;

/**
 *
 * @author Daniel Fern&aacute;ndez
 *
 * @since 3.0.12
 *
 */
public final class SpringStandardExpressionUtils {


    private static final char[] NEW_ARRAY = "wen".toCharArray(); // Inverted "new"
    private static final int NEW_LEN = NEW_ARRAY.length;
    private static final char[] PARAM_ARRAY = "marap".toCharArray(); // Inverted "param"
    private static final int PARAM_LEN = PARAM_ARRAY.length;


    public static boolean containsExternalAccess(final String expression) {

        /*
         * Checks whether the expression contains instantiation of objects ("new SomeClass") or makes use of
         * static methods ("T(SomeClass)") as both are forbidden in certain contexts in restricted mode.
         *
         * Kedos: the contents of SpEL string literals are blanked out before scanning. Literal text cannot execute,
         * and since 3.1.6 lower-cases the expression for matching (SpEL keywords are case-insensitive), literals
         * such as 'New promotion', 'Search params' or 'a@b.com' would otherwise be reported. Literal boundaries
         * are found on the ORIGINAL expression with exactly SpEL's own rules (see blankStringLiterals) before
         * normalization, which deletes control characters and could otherwise change where a literal ends.
         */

        final String exp = ExpressionUtils.normalize(blankStringLiterals(expression), true);

        if (containsBeanReference(exp)) {
            return true;
        }

        final int explen = exp.length();
        int n = explen;
        int ni = 0; // index for computing position in the NEW_ARRAY
        int pi = 0; // index for computing position in the PARAM_ARRAY
        char c;
        while (n-- != 0) {

            c = exp.charAt(n);

            // When checking for the "new" keyword, we need to identify that it is not a part of a larger
            // identifier, i.e. there is whitespace after it and no character that might be a part of an
            // identifier before it.
            if (ni < NEW_LEN
                    && c == NEW_ARRAY[ni]
                    && (ni > 0 || ((n + 1 < explen) && Character.isWhitespace(exp.charAt(n + 1))))) {
                ni++;
                if (ni == NEW_LEN && (n == 0 || !isSafeIdentifierChar(exp.charAt(n - 1)))) {
                    return true; // we found an object instantiation
                }
                continue;
            }

            if (ni > 0) {
                // We 'restart' the matching counter just in case we had a partial match
                n += ni;
                ni = 0;
                continue;
            }

            ni = 0;

            // When checking for the "param" keyword, we need to identify that it is not a part of a larger
            // identifier.
            if (pi < PARAM_LEN
                    && c == PARAM_ARRAY[pi]
                    && (pi > 0 || ((n + 1 < explen) && !isSafeIdentifierChar(exp.charAt(n + 1))))) {
                pi++;
                if (pi == PARAM_LEN && (n == 0 || !isSafeIdentifierChar(exp.charAt(n - 1)))) {
                    return true; // we found a param access
                }
                continue;
            }

            if (pi > 0) {
                // We 'restart' the matching counter just in case we had a partial match
                n += pi;
                pi = 0;
                continue;
            }

            pi = 0;

            if (c == '(' && ((n - 1 >= 0) && isPreviousStaticMarker(exp, n))) {
                return true;
            }

            // Bean references: @beanName, and (Kedos) also SpEL's quoted form @'bean name' / @"bean name"
            if (c == '@' &&
                    ((n + 1 < explen) && isBeanReferenceStart(exp.charAt(n + 1))) &&
                    (n == 0  || !isSafeIdentifierChar(exp.charAt(n - 1)))) {
                return true;
            }

        }

        return false;

    }


    /*
     * Replaces the contents of every complete SpEL string literal with 'x', keeping the quotes and the length.
     * Mirrors org.springframework.expression.spel.standard.Tokenizer exactly: outside a literal, ' or " starts one;
     * inside it, a doubled quote of the same kind is an escaped quote, and the next single quote of that kind ends
     * it. No other escape exists, and no other SpEL token can contain a quote character. An unterminated literal
     * (which SpEL refuses to parse) is left as it is, so its text is still scanned.
     */
    static String blankStringLiterals(final String expression) {
        if (expression == null || (expression.indexOf('\'') < 0 && expression.indexOf('"') < 0)) {
            return expression;
        }
        final char[] chars = expression.toCharArray();
        int i = 0;
        while (i < chars.length) {
            final char quote = chars[i];
            if (quote != '\'' && quote != '"') {
                i++;
                continue;
            }
            final int end = findLiteralEnd(chars, i, quote);
            if (end < 0) {
                break; // Unterminated: leave the rest untouched (conservative)
            }
            for (int j = i + 1; j < end; j++) {
                chars[j] = 'x';
            }
            i = end + 1;
        }
        return new String(chars);
    }


    private static int findLiteralEnd(final char[] chars, final int start, final char quote) {
        int i = start + 1;
        while (i < chars.length) {
            if (chars[i] == quote) {
                if (i + 1 < chars.length && chars[i + 1] == quote) {
                    i += 2; // Escaped quote, still inside the literal
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }


    private static boolean isPreviousStaticMarker(final String expression, final int idx) {
        char c,c1;
        int n = idx;
        while (n-- != 0) {
            c = expression.charAt(n);
            if (c == 't') {
                if (n == 0) {
                    return true;
                }
                c1 = expression.charAt(n - 1);
                return !isSafeIdentifierChar(c1);
            } else if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return false;
    }


    private static boolean isBeanReferenceStart(final char c) {
        return isSafeIdentifierChar(c) || c == '\'' || c == '"';
    }


    /*
     * Kedos: bean references as SpEL tokenizes and parses them (Tokenizer, InternalSpelExpressionParser
     * .maybeEatBeanReference): a BEAN_REF token '@' or a FACTORY_BEAN_REF token '&' (a single '&'; '&&' is the
     * logical AND token, consumed left to right), then, after any whitespace, an identifier or a string literal.
     * Scanned left to right on the literal-blanked, normalized expression.
     */
    private static boolean containsBeanReference(final String exp) {
        final int len = exp.length();
        int i = 0;
        while (i < len) {
            final char c = exp.charAt(i);
            if (c == '&' && i + 1 < len && exp.charAt(i + 1) == '&') {
                i += 2; // Logical AND
                continue;
            }
            if (c == '@' || c == '&') {
                int j = i + 1;
                while (j < len && Character.isWhitespace(exp.charAt(j))) {
                    j++;
                }
                if (j < len && isBeanNameStart(exp.charAt(j))) {
                    return true;
                }
            }
            i++;
        }
        return false;
    }


    private static boolean isBeanNameStart(final char c) {
        return Character.isLetter(c) || c == '_' || c == '$' || c == '\'' || c == '"';
    }


    private static boolean isSafeIdentifierChar(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }


    private SpringStandardExpressionUtils() {
        super();
    }



}
