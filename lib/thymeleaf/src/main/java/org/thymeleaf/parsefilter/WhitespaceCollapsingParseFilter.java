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
import org.thymeleaf.engine.WhitespaceCollapsingTemplateHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * <p>
 *   Parse filter that replaces every whitespace-only text in HTML template markup with a single newline,
 *   except inside {@code pre}, {@code textarea}, {@code script} and {@code style} elements.
 *   See {@link WhitespaceCollapsingTemplateHandler} for the exact rules.
 * </p>
 */
public final class WhitespaceCollapsingParseFilter implements IParseFilter {

    public static final int DEFAULT_PRECEDENCE = 1000;

    private final int precedence;


    public WhitespaceCollapsingParseFilter() {
        this(DEFAULT_PRECEDENCE);
    }

    public WhitespaceCollapsingParseFilter(final int precedence) {
        super();
        this.precedence = precedence;
    }


    public TemplateMode getTemplateMode() {
        return TemplateMode.HTML;
    }

    public int getPrecedence() {
        return this.precedence;
    }

    public ITemplateHandler createHandler(final IEngineConfiguration configuration, final ITemplateHandler next) {
        return new WhitespaceCollapsingTemplateHandler(next);
    }

}
