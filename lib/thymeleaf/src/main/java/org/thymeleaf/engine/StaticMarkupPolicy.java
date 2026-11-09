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
package org.thymeleaf.engine;

import java.util.Set;

import org.thymeleaf.DialectConfiguration;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.dialect.IProcessorDialect;
import org.thymeleaf.model.ICDATASection;
import org.thymeleaf.model.IComment;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.model.IText;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.cdatasection.ICDATASectionProcessor;
import org.thymeleaf.processor.comment.ICommentProcessor;
import org.thymeleaf.processor.element.IElementModelProcessor;
import org.thymeleaf.processor.element.IElementProcessor;
import org.thymeleaf.processor.text.ITextProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.standard.processor.StandardConditionalCommentProcessor;
import org.thymeleaf.standard.processor.StandardInliningCDATASectionProcessor;
import org.thymeleaf.standard.processor.StandardInliningCommentProcessor;
import org.thymeleaf.standard.processor.StandardInliningTextProcessor;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.util.ProcessorConfigurationUtils;

/**
 * <p>
 *   Decides, for one engine configuration, which parsed template events are <em>static</em>: their output can be
 *   rendered once at parse time and will be identical on every render.
 * </p>
 * <ul>
 *   <li>Element tags are static when no processor is associated with them.</li>
 *   <li>Texts, comments and CDATA sections are static when they contain no {@code [} (no inlining syntax of any
 *       inlining mode, no conditional comments) and every processor configured for their kind is one of the known
 *       standard inlining processors.</li>
 *   <li>DOCTYPEs, XML declarations, processing instructions and unmatched close tags are never static.</li>
 * </ul>
 * <p>
 *   Coalescing is disabled entirely when pre-processors or post-processors are configured for the template mode
 *   (they observe the event stream, which coalescing changes), or when a dialect that is not trusted has element
 *   model processors (they may inspect parsed markup).
 * </p>
 */
final class StaticMarkupPolicy {

    private final String disabledReason;
    private final boolean textsCoalescible;
    private final boolean commentsCoalescible;
    private final boolean cdataSectionsCoalescible;


    static StaticMarkupPolicy forConfiguration(
            final IEngineConfiguration configuration, final TemplateMode templateMode,
            final Set<Class<? extends IDialect>> trustedDialectTypes) {
        return new StaticMarkupPolicy(
                computeDisabledReason(configuration, templateMode, trustedDialectTypes),
                onlyStandardTextProcessors(configuration.getTextProcessors(templateMode)),
                onlyStandardCommentProcessors(configuration.getCommentProcessors(templateMode)),
                onlyStandardCDATASectionProcessors(configuration.getCDATASectionProcessors(templateMode)));
    }


    private StaticMarkupPolicy(
            final String disabledReason, final boolean textsCoalescible,
            final boolean commentsCoalescible, final boolean cdataSectionsCoalescible) {
        super();
        this.disabledReason = disabledReason;
        this.textsCoalescible = textsCoalescible;
        this.commentsCoalescible = commentsCoalescible;
        this.cdataSectionsCoalescible = cdataSectionsCoalescible;
    }


    boolean isEnabled() {
        return this.disabledReason == null;
    }

    String getDisabledReason() {
        return this.disabledReason;
    }


    boolean isStaticLeaf(final ITemplateEvent event) {
        if (event instanceof IText) {
            return this.textsCoalescible && hasNoBracket((IText) event);
        }
        if (event instanceof IStandaloneElementTag) {
            return !StandaloneElementTag.asEngineStandaloneElementTag((IStandaloneElementTag) event).hasAssociatedProcessors();
        }
        if (event instanceof IComment) {
            final IComment comment = (IComment) event;
            return this.commentsCoalescible && hasNoBracket(comment.getContent());
        }
        if (event instanceof ICDATASection) {
            return this.cdataSectionsCoalescible && hasNoBracket(((ICDATASection) event).getContent());
        }
        return false; // DOCTYPE, XML declaration, processing instruction, unmatched close tag
    }


    /*
     * Text, comments and CDATA are only static when they contain no '[' at all. Every inlining syntax starts with
     * '[': [[...]] and [(...)] in HTML text, and in switched modes (th:inline="javascript", "css" or "text" set on
     * any ancestor at runtime) also element blocks [# ...]...[/] and the "added" / "removed" comment blocks
     * (slash-star-[+ and slash-star-[-). Whether an ancestor switches the inlining mode is only known at render
     * time, so any '[' keeps the event dynamic.
     */
    private static boolean hasNoBracket(final CharSequence content) {
        for (int i = 0, n = content.length(); i < n; i++) {
            if (content.charAt(i) == '[') {
                return false;
            }
        }
        return true;
    }


    boolean isStaticTag(final IOpenElementTag openElementTag) {
        return !OpenElementTag.asEngineOpenElementTag(openElementTag).hasAssociatedProcessors();
    }


    /**
     * @return true if a processor associated with this tag receives the element's whole model, in which case
     *         nothing inside the element may be coalesced.
     */
    boolean isModelSensitive(final IOpenElementTag openElementTag) {
        for (final IElementProcessor processor : OpenElementTag.asEngineOpenElementTag(openElementTag).getAssociatedProcessors()) {
            if (processor instanceof IElementModelProcessor) {
                return true;
            }
        }
        return false;
    }


    private static String computeDisabledReason(
            final IEngineConfiguration configuration, final TemplateMode templateMode,
            final Set<Class<? extends IDialect>> trustedDialectTypes) {
        final String untrusted = findUntrustedModelProcessorDialect(configuration, templateMode, trustedDialectTypes);
        if (untrusted != null) {
            return "dialect \"" + untrusted + "\" has element model processors (which may inspect the parsed "
                    + "markup of this or other templates) and is not trusted for coalescing";
        }
        if (!configuration.getPreProcessors(templateMode).isEmpty()) {
            return "pre-processors are configured for template mode " + templateMode;
        }
        if (!configuration.getPostProcessors(templateMode).isEmpty()) {
            return "post-processors are configured for template mode " + templateMode;
        }
        return null;
    }


    /*
     * Model processors receive parsed models and can inspect the markup of the current template and of other
     * templates they load (e.g. layout/decorator dialects walk a decorator's <head>). Coalesced markup would hide
     * elements from them, so coalescing requires every dialect that has model processors to be explicitly trusted.
     * The Standard dialect (and its Spring subclass) has none.
     */
    private static String findUntrustedModelProcessorDialect(
            final IEngineConfiguration configuration, final TemplateMode templateMode,
            final Set<Class<? extends IDialect>> trustedDialectTypes) {
        for (final DialectConfiguration dialectConfiguration : configuration.getDialectConfigurations()) {
            final IDialect dialect = dialectConfiguration.getDialect();
            if (!(dialect instanceof IProcessorDialect) || isTrusted(dialect, trustedDialectTypes)) {
                continue;
            }
            final String prefix = (dialectConfiguration.isPrefixSpecified() ?
                    dialectConfiguration.getPrefix() : ((IProcessorDialect) dialect).getPrefix());
            final Set<IProcessor> processors = ((IProcessorDialect) dialect).getProcessors(prefix);
            if (processors == null) {
                continue;
            }
            for (final IProcessor processor : processors) {
                if (processor instanceof IElementModelProcessor && processor.getTemplateMode() == templateMode) {
                    return dialect.getName();
                }
            }
        }
        return null;
    }


    private static boolean isTrusted(final IDialect dialect, final Set<Class<? extends IDialect>> trustedDialectTypes) {
        if (dialect instanceof StandardDialect) {
            return true;
        }
        for (final Class<? extends IDialect> trustedType : trustedDialectTypes) {
            if (trustedType.isInstance(dialect)) {
                return true;
            }
        }
        return false;
    }


    private static boolean onlyStandardTextProcessors(final Set<ITextProcessor> processors) {
        for (final ITextProcessor processor : processors) {
            if (!(ProcessorConfigurationUtils.unwrap(processor) instanceof StandardInliningTextProcessor)) {
                return false;
            }
        }
        return true;
    }


    private static boolean onlyStandardCommentProcessors(final Set<ICommentProcessor> processors) {
        for (final ICommentProcessor processor : processors) {
            final ICommentProcessor unwrapped = ProcessorConfigurationUtils.unwrap(processor);
            if (!(unwrapped instanceof StandardInliningCommentProcessor)
                    && !(unwrapped instanceof StandardConditionalCommentProcessor)) {
                return false;
            }
        }
        return true;
    }


    private static boolean onlyStandardCDATASectionProcessors(final Set<ICDATASectionProcessor> processors) {
        for (final ICDATASectionProcessor processor : processors) {
            if (!(ProcessorConfigurationUtils.unwrap(processor) instanceof StandardInliningCDATASectionProcessor)) {
                return false;
            }
        }
        return true;
    }

}
