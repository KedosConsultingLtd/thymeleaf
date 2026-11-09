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
import java.util.Set;

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IParseFilterDialect;

/**
 * <p>
 *   Dialect enabling {@link WhitespaceCollapsingParseFilter}. Register it with the template engine
 *   (e.g. as a Spring bean when using Spring Boot's Thymeleaf auto-configuration) to collapse
 *   whitespace-only template text at parse time.
 * </p>
 */
public final class WhitespaceCollapsingDialect extends AbstractDialect implements IParseFilterDialect {

    public static final String NAME = "WhitespaceCollapsing";

    private final Set<IParseFilter> parseFilters;


    public WhitespaceCollapsingDialect() {
        this(WhitespaceCollapsingParseFilter.DEFAULT_PRECEDENCE);
    }

    public WhitespaceCollapsingDialect(final int precedence) {
        super(NAME);
        this.parseFilters =
                Collections.<IParseFilter>singleton(new WhitespaceCollapsingParseFilter(precedence));
    }


    public Set<IParseFilter> getParseFilters() {
        return this.parseFilters;
    }

}
