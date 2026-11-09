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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.model.ICDATASection;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IComment;
import org.thymeleaf.model.IDocType;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessingInstruction;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEnd;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.model.IText;
import org.thymeleaf.model.IXMLDeclaration;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * <p>
 *   Parse-time handler that merges runs of static markup (elements without processors, non-inlineable text, ...)
 *   into single pre-rendered text events. The rendered output is unchanged; the template model just has far fewer
 *   events to dispatch, track and write on every render.
 * </p>
 * <p>
 *   Buffers the events of one parsed template between template start and end, then emits the coalesced events.
 *   See {@link StaticMarkupPolicy} for what counts as static and {@link StaticRunEmitter} for which element
 *   events are always kept. Instances are stateful and must be used for a single parse operation only.
 * </p>
 */
public final class StaticRunCoalescingTemplateHandler extends AbstractTemplateHandler {

    private static final Logger logger = LoggerFactory.getLogger(StaticRunCoalescingTemplateHandler.class);
    private static final AtomicBoolean disabledWarningLogged = new AtomicBoolean(false);

    private final StaticMarkupPolicy policy;
    private final List<ITemplateEvent> buffer = new ArrayList<ITemplateEvent>(64);


    /**
     * @param trustedDialectTypes dialects (besides the Standard dialect) whose element model processors are known not
     *        to depend on the element structure of static markup.
     * @return a coalescing handler writing to {@code next}, or {@code next} itself if the configuration does not
     *         allow coalescing.
     */
    public static ITemplateHandler create(
            final IEngineConfiguration configuration, final TemplateMode templateMode, final ITemplateHandler next,
            final Set<Class<? extends IDialect>> trustedDialectTypes) {
        final StaticMarkupPolicy policy =
                StaticMarkupPolicy.forConfiguration(configuration, templateMode, trustedDialectTypes);
        if (!policy.isEnabled()) {
            if (disabledWarningLogged.compareAndSet(false, true)) {
                logger.warn("[THYMELEAF] Static-run coalescing is disabled: {}", policy.getDisabledReason());
            }
            return next;
        }
        return new StaticRunCoalescingTemplateHandler(policy, next);
    }


    private StaticRunCoalescingTemplateHandler(final StaticMarkupPolicy policy, final ITemplateHandler next) {
        super(next);
        this.policy = policy;
    }


    @Override
    public void handleTemplateStart(final ITemplateStart templateStart) {
        this.buffer.clear();
        super.handleTemplateStart(templateStart);
    }


    @Override
    public void handleTemplateEnd(final ITemplateEnd templateEnd) {
        final List<StaticMarkupNode> roots = StaticMarkupTreeBuilder.build(this.buffer, this.policy);
        if (roots == null) {
            emitUnchanged();
        } else {
            new StaticRunEmitter(getNext()).emit(roots);
        }
        this.buffer.clear();
        super.handleTemplateEnd(templateEnd);
    }


    private void emitUnchanged() {
        for (final ITemplateEvent event : this.buffer) {
            TemplateEventDispatcher.dispatch(event, getNext());
        }
    }


    @Override
    public void handleText(final IText text) {
        this.buffer.add(text);
    }

    @Override
    public void handleOpenElement(final IOpenElementTag openElementTag) {
        this.buffer.add(openElementTag);
    }

    @Override
    public void handleCloseElement(final ICloseElementTag closeElementTag) {
        this.buffer.add(closeElementTag);
    }

    @Override
    public void handleStandaloneElement(final IStandaloneElementTag standaloneElementTag) {
        this.buffer.add(standaloneElementTag);
    }

    @Override
    public void handleComment(final IComment comment) {
        this.buffer.add(comment);
    }

    @Override
    public void handleCDATASection(final ICDATASection cdataSection) {
        this.buffer.add(cdataSection);
    }

    @Override
    public void handleDocType(final IDocType docType) {
        this.buffer.add(docType);
    }

    @Override
    public void handleXMLDeclaration(final IXMLDeclaration xmlDeclaration) {
        this.buffer.add(xmlDeclaration);
    }

    @Override
    public void handleProcessingInstruction(final IProcessingInstruction processingInstruction) {
        this.buffer.add(processingInstruction);
    }

}
