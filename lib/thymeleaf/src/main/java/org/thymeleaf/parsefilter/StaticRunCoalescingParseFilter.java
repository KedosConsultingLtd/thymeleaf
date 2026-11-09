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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.engine.ITemplateHandler;
import org.thymeleaf.engine.StaticRunCoalescingTemplateHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * <p>
 *   Parse filter that merges runs of static HTML markup (elements without processors, non-inlineable text and
 *   comments) into single pre-rendered text events, so they cost one event per render instead of one per tag.
 *   Output is unchanged. See {@link StaticRunCoalescingTemplateHandler}.
 * </p>
 * <p>
 *   Disables itself (with a warning) when pre-processors or post-processors are configured, because those
 *   observe the event stream, or when a dialect with element model processors is configured that has not been
 *   explicitly trusted (see {@link #StaticRunCoalescingParseFilter(int, Set)}).
 * </p>
 */
public final class StaticRunCoalescingParseFilter implements IParseFilter {

    /** Runs after {@link WhitespaceCollapsingParseFilter#DEFAULT_PRECEDENCE}, so collapsed whitespace is merged too. */
    public static final int DEFAULT_PRECEDENCE = 2000;

    private final int precedence;
    private final Set<Class<? extends IDialect>> trustedDialectTypes;


    public StaticRunCoalescingParseFilter() {
        this(DEFAULT_PRECEDENCE, Collections.<Class<? extends IDialect>>emptySet());
    }

    /**
     * @param precedence the filter precedence.
     * @param trustedDialectTypes dialects (besides the Standard dialect) whose element model processors may be
     *        used together with coalescing. Only list dialects whose model processors never inspect the element
     *        structure of static markup (in this template or in templates/fragments they load). If any other
     *        dialect with element model processors is configured, coalescing disables itself.
     */
    public StaticRunCoalescingParseFilter(final int precedence, final Set<Class<? extends IDialect>> trustedDialectTypes) {
        super();
        this.precedence = precedence;
        this.trustedDialectTypes = Collections.unmodifiableSet(new HashSet<Class<? extends IDialect>>(trustedDialectTypes));
    }


    public TemplateMode getTemplateMode() {
        return TemplateMode.HTML;
    }

    public int getPrecedence() {
        return this.precedence;
    }

    public ITemplateHandler createHandler(final IEngineConfiguration configuration, final ITemplateHandler next) {
        return StaticRunCoalescingTemplateHandler.create(configuration, TemplateMode.HTML, next, this.trustedDialectTypes);
    }

}
