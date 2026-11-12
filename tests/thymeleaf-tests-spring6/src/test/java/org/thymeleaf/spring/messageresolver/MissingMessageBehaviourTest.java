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
package org.thymeleaf.spring.messageresolver;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.messageresolver.AbstractMessageResolver;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.messageresolver.SpringMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Missing-message behaviour with a Spring MessageSource that throws NoSuchMessageException for unknown keys
 * (useCodeAsDefaultMessage=false, Spring's default).
 * <ul>
 *   <li>{@code #messages.msgOrNull(...)} must return null, whatever the configuration (3.1.6 regression: it threw).</li>
 *   <li>{@code #{...}} and {@code #messages.msg(...)} render {@code ??key_locale??} by default (3.1.4 and 3.1.7
 *       behaviour), and fail only with failOnMissingMessage=true (opt-in since 3.1.7).</li>
 *   <li>A resolver signalling "not found" does not stop later resolvers from being tried.</li>
 * </ul>
 */
public class MissingMessageBehaviourTest {

    private static final String OR_NULL =
            "[[${#messages.msgOrNull('missing.key') ?: 'fallback'}]]|[[${#messages.msgOrNull('missing.key', 1) ?: 'fallback'}]]";


    @Test
    public void msgOrNullReturnsNullForMissingKeyByDefault() {
        Assertions.assertEquals("fallback|fallback", engine(true).process(OR_NULL, context()));
    }

    @Test
    public void msgOrNullReturnsNullForMissingKeyWithoutFailOnMissing() {
        Assertions.assertEquals("fallback|fallback", engine(false).process(OR_NULL, context()));
    }

    @Test
    public void msgOrNullStillResolvesExistingKey() {
        Assertions.assertEquals("Hello",
                engine(true).process("[[${#messages.msgOrNull('greeting') ?: 'fallback'}]]", context()));
    }

    @Test
    public void missingKeyRendersAbsentRepresentationByDefault() {
        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(textResolver());
        engine.setTemplateEngineMessageSource(messageSource());
        Assertions.assertEquals("??missing.key_en??|??missing.key_en??|fallback",
                engine.process("[[#{missing.key}]]|[[${#messages.msg('missing.key')}]]|[[${#messages.msgOrNull('missing.key') ?: 'fallback'}]]", context()));
    }

    @Test
    public void missingKeyFailsWhenOptedIn() {
        final SpringTemplateEngine engine = engine(true);
        assertMessageNotFound(() -> engine.process("[[#{missing.key}]]", context()));
        assertMessageNotFound(() -> engine.process("[[${#messages.msg('missing.key')}]]", context()));
    }

    @Test
    public void missingKeyRendersAbsentRepresentationWhenNotFailing() {
        Assertions.assertEquals("??missing.key_en??|Hello",
                engine(false).process("[[#{missing.key}]]|[[#{greeting}]]", context()));
    }

    @Test
    public void laterResolversAreStillTriedAfterSpringResolverReportsMissingKey() {
        final SpringMessageResolver spring = new SpringMessageResolver();
        spring.setMessageSource(messageSource());
        spring.setFailOnMissingMessage(true);
        spring.setOrder(1);
        final FixedMessageResolver fallbackResolver = new FixedMessageResolver("only.in.second", "From second");
        fallbackResolver.setOrder(2);
        final Set<IMessageResolver> resolvers = new LinkedHashSet<>();
        resolvers.add(spring);
        resolvers.add(fallbackResolver);

        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(textResolver());
        engine.setMessageResolvers(resolvers);

        Assertions.assertEquals("From second|From second",
                engine.process("[[#{only.in.second}]]|[[${#messages.msgOrNull('only.in.second')}]]", context()));
        assertMessageNotFound(() -> engine.process("[[#{missing.key}]]", context()));
    }


    private static SpringTemplateEngine engine(final boolean failOnMissingMessage) {
        final SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(textResolver());
        engine.setTemplateEngineMessageSource(messageSource());
        engine.setFailOnMissingMessage(failOnMissingMessage);
        return engine;
    }

    private static StaticMessageSource messageSource() {
        final StaticMessageSource messageSource = new StaticMessageSource(); // useCodeAsDefaultMessage=false
        messageSource.addMessage("greeting", Locale.ENGLISH, "Hello");
        return messageSource;
    }

    private static StringTemplateResolver textResolver() {
        final StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        return resolver;
    }

    private static Context context() {
        return new Context(Locale.ENGLISH);
    }

    private static void assertMessageNotFound(final org.junit.jupiter.api.function.Executable executable) {
        Assertions.assertThrows(TemplateProcessingException.class, executable);
    }


    private static final class FixedMessageResolver extends AbstractMessageResolver {

        private final String key;
        private final String message;

        FixedMessageResolver(final String key, final String message) {
            this.key = key;
            this.message = message;
        }

        public String resolveMessage(
                final ITemplateContext context, final Class<?> origin, final String key, final Object[] messageParameters) {
            return this.key.equals(key) ? this.message : null;
        }

        public String createAbsentMessageRepresentation(
                final ITemplateContext context, final Class<?> origin, final String key, final Object[] messageParameters) {
            return null;
        }

    }

}
