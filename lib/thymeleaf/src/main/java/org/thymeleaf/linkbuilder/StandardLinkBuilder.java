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
package org.thymeleaf.linkbuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.thymeleaf.context.Contexts;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.util.Validate;
import org.unbescape.uri.UriEscape;


/**
 * <p>
 *   Standard implementation of {@link ILinkBuilder}.
 * </p>
 * <p>
 *   This class will build link URLs using (by default) the Java Servlet API when the specified URLs are
 *   context-relative, given the need to obtain the context path and add it to the URL. Also, when an
 *   {@link IWebContext} implementation is used as context, URLs will be passed to
 *   the standard {@code HttpSerlvetResponse.encodeURL(...)} method before returning.
 * </p>
 * <p>
 *   Note however that the Servlet-API specific part of this behaviour is configurable and confined to a set of
 *   {@code protected} methods that can be overwritten by subclasses that want to offer a link building
 *   behaviour very similar to the standard one, but without any dependencies on the Servlet API (e.g. extracting
 *   URL context paths from a framework artifact other than {@code HttpServletRequest}).
 * </p>
 * <p>
 *   This implementation will only return {@code null} at {@link #buildLink(IExpressionContext, String, Map)}
 *   if the specified {@code base} argument is {@code null}.
 * </p>
 *
 * @author Daniel Fern&aacute;ndez
 *
 * @since 3.0.0
 */
public class StandardLinkBuilder extends AbstractLinkBuilder {

    protected enum LinkType { ABSOLUTE, CONTEXT_RELATIVE, SERVER_RELATIVE, BASE_RELATIVE }

    private static final char URL_TEMPLATE_DELIMITER_PREFIX = '{';
    private static final char URL_TEMPLATE_DELIMITER_SUFFIX = '}';
    private static final String URL_TEMPLATE_DELIMITER_SEGMENT_PREFIX = "{/";

    private static final String JAVASCRIPT_SCHEME = "javascript:";
    private static final int JAVASCRIPT_SCHEME_LEN = JAVASCRIPT_SCHEME.length();



    public StandardLinkBuilder() {
        super();
    }

    


    public final String buildLink(
            final IExpressionContext context, final String base, final Map<String, Object> parameters) {

        Validate.notNull(context, "Expression context cannot be null");

        if (base == null) {
            return null;
        }

        // We need to create a copy that is: 1. defensive, 2. mutable
        // Kedos: parameters are only read, never modified, so no defensive copy is needed. Parameters used as path
        // variables are recorded in 'pathVariableNames' and skipped when building the query string.
        final Map<String,Object> linkParameters = (parameters == null || parameters.size() == 0? null : parameters);

        filterOutJavaScriptLinks(base);

        final LinkType linkType;
        if (isLinkBaseAbsolute(base)) {
            linkType = LinkType.ABSOLUTE;
        } else if (isLinkBaseContextRelative(base)) {
            linkType = LinkType.CONTEXT_RELATIVE;
        } else if (isLinkBaseServerRelative(base)) {
            linkType = LinkType.SERVER_RELATIVE;
        } else {
            linkType = LinkType.BASE_RELATIVE;
        }


        /*
         * Compute URL fragments (selectors after '#') so that they can be output at the end of
         * the URL, after parameters.
         */
        final int hashPosition = findCharInSequence(base, '#');


        /*
         * Compute whether we might have variable templates (e.g. Spring Path Variables) inside this link base
         * that we might need to resolve afterwards
         */
        final boolean mightHaveVariableTemplates = findCharInSequence(base, URL_TEMPLATE_DELIMITER_PREFIX) >= 0;


        /*
         * Precompute the context path, so that it can be afterwards used for determining if it has to be added to the
         * URL (in case it is context-relative) or not.
         *
         * Note we give subclasses the opportunity to customize the computation of this context path.
         */
        final String contextPath =
                (linkType == LinkType.CONTEXT_RELATIVE? computeContextPath(context, base, parameters) : null);
        final boolean contextPathEmpty = contextPath == null || contextPath.length() == 0 || contextPath.equals("/");


        /*
         * SHORTCUT - just before starting to work with StringBuilders, and in the case that we know: 1. That the URL is
         *            absolute, relative or context-relative with no context; 2. That there are no parameters; and
         *            3. That there are no URL fragments -> then just return the base URL String without further
         *            processing (except HttpServletResponse-encoding if needed, of course...)
         */
        if (contextPathEmpty && linkType != LinkType.SERVER_RELATIVE &&
                (linkParameters == null || linkParameters.size() == 0) && hashPosition < 0 && !mightHaveVariableTemplates) {
            return processLink(context, base);
        }


        /*
         * Build the StringBuilder that will be used as a base for all URL-related operations from now on: variable
         * templates, parameters, URL fragments...
         */
        /*
         * Build the link base. For server-relative links the leading '~' is simply not copied (previously it was
         * deleted at the end, which shifted the whole buffer; no step in between depends on it).
         */
        final int baseStart = (linkType == LinkType.SERVER_RELATIVE ? 1 : 0);
        final int baseEnd = (hashPosition > 0 ? hashPosition : base.length());
        final StringBuilder linkBase = new StringBuilder(base.length() + 32);
        linkBase.append(base, baseStart, baseEnd);
        final String urlFragment = (hashPosition > 0 ? base.substring(hashPosition) : "");

        /*
         * Replace those variable templates that might appear
         */
        List<String> pathVariableNames = null;
        if (mightHaveVariableTemplates) {
            pathVariableNames = replaceTemplateParamsInBase(linkBase, linkParameters);
            filterOutJavaScriptLinks(linkBase);
        }

        /*
         * Process parameters (those that have not already been processed as a result of replacing template
         * parameters in base).
         */
        final int pathVariableCount = (pathVariableNames == null ? 0 : pathVariableNames.size());
        if (linkParameters != null && linkParameters.size() > pathVariableCount) {

            final boolean linkBaseHasQuestionMark = findCharInSequence(linkBase,'?') >= 0;

            if (linkBaseHasQuestionMark) {
                linkBase.append('&');
            } else {
                linkBase.append('?');
            }

            processAllRemainingParametersAsQueryParams(linkBase, linkParameters, pathVariableNames);

        }

        /*
         * Add the fragment again
         */
        if (urlFragment.length() > 0) {
            linkBase.append(urlFragment);
        }

        /*
         * Context is not empty and URL is context relative: insert the context path
         */
        if (linkType == LinkType.CONTEXT_RELATIVE && !contextPathEmpty) {
            linkBase.insert(0, contextPath);
        }

        return processLink(context, linkBase.toString());

    }

    


    private static int findCharInSequence(final CharSequence seq, final char character) {
        int n = seq.length();
        while (n-- != 0) {
            final char c = seq.charAt(n);
            if (c == character) {
                return n;
            }
        }
        return -1;
    }


    private static void filterOutJavaScriptLinks(final CharSequence linkBase) {

        // Scheme is read as in the WHATWG URL parser: leading C0 controls and spaces are skipped,
        // and ASCII tabs and newlines are ignored anywhere
        final int linkBaseLen = linkBase.length();
        int i = 0;
        while (i < linkBaseLen && linkBase.charAt(i) <= ' ') {
            i++;
        }

        int matched = 0;
        while (i < linkBaseLen && matched < JAVASCRIPT_SCHEME_LEN) {
            final char c = linkBase.charAt(i++);
            if (c == '\t' || c == '\n' || c == '\r') {
                continue;
            }
            if (Character.toLowerCase(c) != JAVASCRIPT_SCHEME.charAt(matched)) {
                return;
            }
            matched++;
        }

        if (matched == JAVASCRIPT_SCHEME_LEN) {
            throw new TemplateProcessingException(
                    "'javascript:' is forbidden in this context. Link expressions cannot " +
                    "contain inlined JavaScript code.");
        }

    }


    
    private static boolean isLinkBaseAbsolute(final CharSequence linkBase) {
        final int linkBaseLen = linkBase.length();
        if (linkBaseLen < 2) {
            return false;
        }
        final char c0 = linkBase.charAt(0);
        if (c0 == 'm' || c0 == 'M') {
            // Let's check for "mailto:"
            if (linkBase.length() >= 7 &&
                    Character.toLowerCase(linkBase.charAt(1)) == 'a' &&
                    Character.toLowerCase(linkBase.charAt(2)) == 'i' &&
                    Character.toLowerCase(linkBase.charAt(3)) == 'l' &&
                    Character.toLowerCase(linkBase.charAt(4)) == 't' &&
                    Character.toLowerCase(linkBase.charAt(5)) == 'o' &&
                    Character.toLowerCase(linkBase.charAt(6)) == ':') {
                return true;
            }
        } else if (c0 == '/') {
            return linkBase.charAt(1) == '/'; // It starts with '//' -> true, any other '/x' -> false
        }
        for (int i = 0; i < (linkBaseLen - 2); i++) {
            // Let's try to find the '://' sequence anywhere in the base --> true
            if (linkBase.charAt(i) == ':' && linkBase.charAt(i + 1) == '/' && linkBase.charAt(i + 2) == '/') {
                return true;
            }
        }
        return false;
    }


    private static boolean isLinkBaseContextRelative(final CharSequence linkBase) {
        // For this to be true, it should start with '/', but not with '//'
        if (linkBase.length() == 0 || linkBase.charAt(0) != '/') {
            return false;
        }
        return linkBase.length() == 1 || linkBase.charAt(1) != '/';
    }

    
    private static boolean isLinkBaseServerRelative(final CharSequence linkBase) {
        // For this to be true, it should start with '~/'
        return (linkBase.length() >= 2 && linkBase.charAt(0) == '~' && linkBase.charAt(1) == '/');
    }

    
    




    /*
     * Replaces every {name} / {/name} variable template in linkBase with the (escaped) value of the parameter of the
     * same name, and returns the names of the parameters used this way (null if none), which must not also be added
     * as query parameters. Parameters are only read.
     *
     * Values are escaped as a URI path ({name}) or path segment ({/name}) when the template appears before the '?'
     * of the link base, and as a query parameter after it.
     */
    private static List<String> replaceTemplateParamsInBase(final StringBuilder linkBase, final Map<String, Object> parameters) {

        if (parameters == null) {
            return null;
        }

        final int questionMarkPosition = findCharInSequence(linkBase, '?');

        List<String> pathVariableNames = null;
        for (final Map.Entry<String, Object> parameter : parameters.entrySet()) {

            final String parameterName = parameter.getKey();

            // We default to escaping as a path, not a path segment
            boolean escapeAsPathSegment = false;
            int templateIndex = indexOfTemplate(linkBase, parameterName, false, 0);
            if (templateIndex < 0) {
                templateIndex = indexOfTemplate(linkBase, parameterName, true, 0);
                if (templateIndex < 0) {
                    // This parameter is not one of those used in path variables
                    continue;
                }
                escapeAsPathSegment = true;
            }

            if (pathVariableNames == null) {
                pathVariableNames = new ArrayList<String>(parameters.size());
            }
            pathVariableNames.add(parameterName);

            // Compute the replacement (unescaped!)
            final String templateReplacement = formatParameterValueAsUnescapedVariableTemplate(parameter.getValue());
            final int templateReplacementLen = templateReplacement.length();
            final int templateLen = parameterName.length() + (escapeAsPathSegment ? 3 : 2);

            int start = templateIndex;
            while (start > -1) {
                // Depending on whether the template appeared before or after the ?, we will apply different escaping
                final String escapedReplacement =
                        (questionMarkPosition == -1 || start < questionMarkPosition?
                                (escapeAsPathSegment ? UriEscape.escapeUriPathSegment(templateReplacement) : UriEscape.escapeUriPath(templateReplacement))
                                : UriEscape.escapeUriQueryParam(templateReplacement));
                linkBase.replace(start, start + templateLen, escapedReplacement);
                start = indexOfTemplate(linkBase, parameterName, escapeAsPathSegment, start + templateReplacementLen);
            }

        }

        return pathVariableNames;

    }


    /*
     * Position of the first "{name}" (or "{/name}" if segment) in the sequence at or after fromIndex, or -1.
     * Equivalent to sequence.indexOf("{" + name + "}", fromIndex) without building the search string.
     */
    static int indexOfTemplate(final CharSequence sequence, final String name, final boolean segment, final int fromIndex) {
        final int prefixLen = (segment ? 2 : 1);
        final int templateLen = name.length() + prefixLen + 1;
        final int last = sequence.length() - templateLen;
        for (int i = Math.max(fromIndex, 0); i <= last; i++) {
            if (sequence.charAt(i) != URL_TEMPLATE_DELIMITER_PREFIX
                    || (segment && sequence.charAt(i + 1) != '/')
                    || sequence.charAt(i + templateLen - 1) != URL_TEMPLATE_DELIMITER_SUFFIX) {
                continue;
            }
            if (regionMatches(sequence, i + prefixLen, name)) {
                return i;
            }
        }
        return -1;
    }


    private static boolean regionMatches(final CharSequence sequence, final int offset, final String name) {
        for (int j = 0, n = name.length(); j < n; j++) {
            if (sequence.charAt(offset + j) != name.charAt(j)) {
                return false;
            }
        }
        return true;
    }



    private static String formatParameterValueAsUnescapedVariableTemplate(final Object parameterValue) {
        // Get the value
        if (parameterValue == null) { // If null (= NO_VALUE), empty String
            return "";
        }
        // If it is not multivalued (e.g. non-List) simply escape and return
        if (!(parameterValue instanceof List<?>)) {
            return parameterValue.toString();
        }
        // It is multivalued, so iterate and escape each item (no need to escape the comma separating them, it's an allowed char)
        final List<?> values = (List<?>)parameterValue;
        final int valuesLen = values.size();
        final StringBuilder strBuilder = new StringBuilder(valuesLen * 16);
        for (int i = 0; i < valuesLen; i++) {
            final Object valueItem = values.get(i);
            if (strBuilder.length() > 0) {
                strBuilder.append(',');
            }
            strBuilder.append(valueItem == null? "" : valueItem.toString());
        }
        return strBuilder.toString();
    }



    private static void processAllRemainingParametersAsQueryParams(
            final StringBuilder strBuilder, final Map<String, Object> parameters, final List<String> pathVariableNames) {

        final int parameterSize = parameters.size();

        if (parameterSize <= 0) {
            return;
        }

        int i = 0;
        for (final Map.Entry<String, Object> parameter : parameters.entrySet()) {

            final String parameterName = parameter.getKey();
            if (pathVariableNames != null && pathVariableNames.contains(parameterName)) {
                continue; // Already used as a path variable
            }

            final Object value = parameter.getValue();

            if (value == null) {
                if (i > 0) {
                    strBuilder.append('&');
                }
                strBuilder.append(UriEscape.escapeUriQueryParam(parameterName));
                i++;
                continue;
            }

            if (!(value instanceof List<?>)) {
                if (i > 0) {
                    strBuilder.append('&');
                }
                strBuilder.append(UriEscape.escapeUriQueryParam(parameterName));
                strBuilder.append('=');
                strBuilder.append(UriEscape.escapeUriQueryParam(value.toString())); // we know it's not null
                i++;
                continue;
            }

            // It is multivalued, so iterate and process each value
            final List<?> values = (List<?>)value;
            final int valuesLen = values.size();
            for (int j = 0; j < valuesLen; j++) {
                final Object valueItem = values.get(j);
                if (i > 0 || j > 0) {
                    strBuilder.append('&');
                }
                strBuilder.append(UriEscape.escapeUriQueryParam(parameterName));
                if (valueItem != null) {
                    strBuilder.append('=');
                    strBuilder.append(UriEscape.escapeUriQueryParam(valueItem.toString()));
                }
            }

            i++;

        }

    }


    /**
     * <p>
     *   Compute the context path to be applied to URLs that have been determined to be context-relative (and therefore
     *   need a context path to be inserted at their beginning).
     * </p>
     * <p>
     *   By default, this method will obtain the context path from {@code HttpServletRequest.getContextPath()},
     *   throwing an exception if {@code context} is not an instance of {@code IWebContext} given context-relative
     *   URLs are (by default) only allowed in web contexts.
     * </p>
     * <p>
     *   This method can be overridden by any subclasses that want to change this behaviour (e.g. in order to
     *   avoid using the Servlet API for resolving context path or to allow context-relative URLs in non-web
     *   contexts).
     * </p>
     *
     * @param context the execution context.
     * @param base the URL base specified.
     * @param parameters the URL parameters specified.
     * @return the context path.
     */
    protected String computeContextPath(
            final IExpressionContext context, final String base, final Map<String, Object> parameters) {

        if (!Contexts.isWebContext(context)) {
            throw new TemplateProcessingException(
                    "Link base \"" + base + "\" cannot be context relative (/...) unless the context " +
                    "used for executing the engine implements the " + IWebContext.class.getName() + " interface");
        }

        // If it is context-relative, it has to be a web context
        return Contexts.getWebExchange(context).getRequest().getApplicationPath();

    }


    /**
     * <p>
     *   Process an already-built URL just before returning it.
     * </p>
     * <p>
     *   By default, this method will apply the {@code HttpServletResponse.encodeURL(url)} mechanism, as standard
     *   when using the Java Servlet API. Note however that this will only be applied if {@code context} is
     *   an implementation of {@code IWebContext} (i.e. the Servlet API will only be applied in web environments).
     * </p>
     * <p>
     *   This method can be overridden by any subclasses that want to change this behaviour (e.g. in order to
     *   avoid using the Servlet API).
     * </p>
     *
     * @param context the execution context.
     * @param link the already-built URL.
     * @return the processed URL, ready to be used.
     */
    protected String processLink(final IExpressionContext context, final String link) {

        if (!Contexts.isWebContext(context)) {
            return link;
        }

        return Contexts.getWebExchange(context).transformURL(link);

    }


}
