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
package org.thymeleaf.benchmarks.engine;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.messageresolver.AbstractMessageResolver;

/**
 * Message resolver for the core engine, backed by a map. Caches {@link MessageFormat}s per key, the way a typical
 * production message source does, so that message resolution cost is comparable to the Spring engine.
 */
final class MapMessageResolver extends AbstractMessageResolver {

    private final Map<String, String> messages;
    private final Map<String, MessageFormat> formats = new ConcurrentHashMap<>();

    MapMessageResolver(final Map<String, String> messages) {
        super();
        this.messages = Map.copyOf(messages);
    }

    public String resolveMessage(
            final ITemplateContext context, final Class<?> origin, final String key, final Object[] messageParameters) {
        final String message = this.messages.get(key);
        if (message == null) {
            return null;
        }
        if (messageParameters == null || messageParameters.length == 0) {
            return message;
        }
        final MessageFormat format = this.formats.computeIfAbsent(key, k -> new MessageFormat(message, EngineKind.LOCALE));
        synchronized (format) {
            return format.format(messageParameters);
        }
    }

    public String createAbsentMessageRepresentation(
            final ITemplateContext context, final Class<?> origin, final String key, final Object[] messageParameters) {
        return "??" + key + "??";
    }

}
