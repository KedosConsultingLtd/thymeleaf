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

import java.util.HashMap;
import java.util.Map;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.cache.AlwaysValidCacheEntryValidity;
import org.thymeleaf.cache.NonCacheableCacheEntryValidity;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolution;
import org.thymeleaf.templateresource.StringTemplateResource;

/**
 * Test resolver serving named templates from memory, cacheable or not.
 */
final class MapTemplateResolver implements ITemplateResolver {

    private final Map<String, String> templates = new HashMap<String, String>();
    private final boolean cacheable;
    private final TemplateMode templateMode;

    MapTemplateResolver(final boolean cacheable) {
        this(cacheable, TemplateMode.HTML);
    }

    MapTemplateResolver(final boolean cacheable, final TemplateMode templateMode) {
        this.cacheable = cacheable;
        this.templateMode = templateMode;
    }

    MapTemplateResolver with(final String name, final String content) {
        this.templates.put(name, content);
        return this;
    }

    public String getName() {
        return "MapTemplateResolver";
    }

    public Integer getOrder() {
        return Integer.valueOf(1);
    }

    public TemplateResolution resolveTemplate(
            final IEngineConfiguration configuration, final String ownerTemplate, final String template,
            final Map<String, Object> templateResolutionAttributes) {
        final String content = this.templates.get(template);
        if (content == null) {
            return null;
        }
        return new TemplateResolution(
                new StringTemplateResource(content), true, this.templateMode, false,
                this.cacheable ? AlwaysValidCacheEntryValidity.INSTANCE : NonCacheableCacheEntryValidity.INSTANCE);
    }

}
