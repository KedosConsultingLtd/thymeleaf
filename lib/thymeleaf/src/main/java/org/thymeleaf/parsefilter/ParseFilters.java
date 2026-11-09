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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.dialect.IParseFilterDialect;
import org.thymeleaf.engine.ITemplateHandler;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.util.Validate;

/**
 * <p>
 *   The parse filters configured for an engine, resolved once per template mode and ordered by precedence.
 * </p>
 * <p>
 *   Used internally by {@link org.thymeleaf.engine.TemplateManager} to wrap the handler a parser writes to.
 * </p>
 */
public final class ParseFilters {

    private static final ParseFilters NONE = new ParseFilters(Collections.<TemplateMode, List<IParseFilter>>emptyMap());

    private static final Comparator<IParseFilter> PRECEDENCE_ORDER = new Comparator<IParseFilter>() {
        public int compare(final IParseFilter o1, final IParseFilter o2) {
            return Integer.compare(o1.getPrecedence(), o2.getPrecedence());
        }
    };

    private final Map<TemplateMode, List<IParseFilter>> filtersByTemplateMode;


    public static ParseFilters forConfiguration(final IEngineConfiguration configuration) {
        Validate.notNull(configuration, "Engine configuration cannot be null");
        final Set<IParseFilterDialect> dialects = configuration.getDialectsOfType(IParseFilterDialect.class);
        if (dialects == null || dialects.isEmpty()) {
            return NONE;
        }
        final Map<TemplateMode, List<IParseFilter>> filtersByTemplateMode =
                new EnumMap<TemplateMode, List<IParseFilter>>(TemplateMode.class);
        for (final IParseFilterDialect dialect : dialects) {
            addFilters(filtersByTemplateMode, dialect);
        }
        for (final Map.Entry<TemplateMode, List<IParseFilter>> entry : filtersByTemplateMode.entrySet()) {
            Collections.sort(entry.getValue(), PRECEDENCE_ORDER);
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return new ParseFilters(Collections.unmodifiableMap(filtersByTemplateMode));
    }


    private static void addFilters(
            final Map<TemplateMode, List<IParseFilter>> filtersByTemplateMode, final IParseFilterDialect dialect) {
        final Set<IParseFilter> filters = dialect.getParseFilters();
        if (filters == null) {
            return;
        }
        for (final IParseFilter filter : filters) {
            Validate.notNull(filter, "Parse filter cannot be null (dialect: " + dialect.getName() + ")");
            Validate.notNull(filter.getTemplateMode(),
                    "Parse filter template mode cannot be null (dialect: " + dialect.getName() + ")");
            List<IParseFilter> modeFilters = filtersByTemplateMode.get(filter.getTemplateMode());
            if (modeFilters == null) {
                modeFilters = new ArrayList<IParseFilter>(2);
                filtersByTemplateMode.put(filter.getTemplateMode(), modeFilters);
            }
            modeFilters.add(filter);
        }
    }


    private ParseFilters(final Map<TemplateMode, List<IParseFilter>> filtersByTemplateMode) {
        super();
        this.filtersByTemplateMode = filtersByTemplateMode;
    }


    public boolean hasFilters(final TemplateMode templateMode) {
        return this.filtersByTemplateMode.containsKey(templateMode);
    }


    /**
     * <p>
     *   Wrap the handler a parser will write to with the filters for the given template mode. Returns
     *   {@code target} itself when there are no filters for that mode.
     * </p>
     *
     * @param templateMode the template mode the template is being parsed in.
     * @param target the handler that must receive the filtered events.
     * @return the handler the parser must write to.
     */
    public ITemplateHandler wrap(final TemplateMode templateMode, final ITemplateHandler target) {
        final List<IParseFilter> filters = this.filtersByTemplateMode.get(templateMode);
        if (filters == null) {
            return target;
        }
        ITemplateHandler handler = target;
        for (int i = filters.size() - 1; i >= 0; i--) {
            handler = filters.get(i).createHandler(handler);
        }
        return handler;
    }

}
