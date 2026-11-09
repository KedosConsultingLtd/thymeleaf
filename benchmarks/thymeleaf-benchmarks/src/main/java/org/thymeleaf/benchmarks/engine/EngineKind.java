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

import java.util.Locale;
import java.util.Map;

import org.springframework.context.support.StaticMessageSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The template engines benchmarked: the core engine (OGNL expressions) and the Spring engine (SpEL expressions,
 * messages from a Spring {@code MessageSource}).
 */
public enum EngineKind {

    STANDARD {
        @Override
        TemplateEngine newEngine(final Map<String, String> messages) {
            final TemplateEngine engine = new TemplateEngine();
            engine.setMessageResolver(new MapMessageResolver(messages));
            return engine;
        }
    },

    SPRING {
        @Override
        TemplateEngine newEngine(final Map<String, String> messages) {
            final StaticMessageSource messageSource = new StaticMessageSource();
            messageSource.setUseCodeAsDefaultMessage(false);
            for (final Map.Entry<String, String> message : messages.entrySet()) {
                messageSource.addMessage(message.getKey(), Locale.UK, message.getValue());
            }
            final SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateEngineMessageSource(messageSource);
            return engine;
        }
    };


    public static final Locale LOCALE = Locale.UK;


    abstract TemplateEngine newEngine(Map<String, String> messages);


    public TemplateEngine create(final Variant variant) {
        final TemplateEngine engine = newEngine(Messages.load());
        engine.setTemplateResolver(templateResolver());
        for (final IDialect dialect : variant.extraDialects()) {
            engine.addDialect(dialect);
        }
        return engine;
    }


    private static ClassLoaderTemplateResolver templateResolver() {
        final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(EngineKind.class.getClassLoader());
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        return resolver;
    }

}
