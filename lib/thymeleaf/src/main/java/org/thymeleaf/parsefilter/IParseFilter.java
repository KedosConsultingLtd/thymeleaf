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

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.engine.ITemplateHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * <p>
 *   A filter applied to the events of a template <strong>while it is being parsed</strong>, i.e. between the
 *   parser and whatever consumes the parsed events: the model builder that feeds the template cache, or the
 *   processing chain when the template is not cacheable.
 * </p>
 * <p>
 *   Because it runs at parse time, a parse filter's output is computed once per parsed template and is then
 *   replayed from the template cache on every render. This makes parse filters the right place for
 *   transformations that depend only on template markup (e.g. whitespace normalisation), as opposed to
 *   {@link org.thymeleaf.postprocessor.IPostProcessor}s, which run on every render.
 * </p>
 * <p>
 *   Parse filters see template markup only. Output produced during processing (e.g. by {@code th:text},
 *   {@code th:utext} or inlined expressions) never passes through them.
 * </p>
 * <p>
 *   Parse filters are contributed by dialects implementing {@link org.thymeleaf.dialect.IParseFilterDialect}.
 * </p>
 */
public interface IParseFilter {

    /**
     * @return the template mode this filter applies to. Templates parsed in any other mode are not filtered.
     */
    public TemplateMode getTemplateMode();

    /**
     * @return the precedence of this filter. Filters with lower precedence values are applied first
     *         (i.e. closer to the parser).
     */
    public int getPrecedence();

    /**
     * <p>
     *   Create the handler that will filter the events of <strong>one</strong> parse operation. A new handler is
     *   requested for every template parsed, so implementations may keep per-template state.
     * </p>
     *
     * @param configuration the configuration of the engine parsing the template.
     * @param next the handler that must receive the (filtered) events.
     * @return the filtering handler, or {@code next} itself if this filter has nothing to do for this configuration.
     */
    public ITemplateHandler createHandler(final IEngineConfiguration configuration, final ITemplateHandler next);

}
