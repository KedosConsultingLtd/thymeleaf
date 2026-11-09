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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.model.IText;
import org.thymeleaf.util.FastStringWriter;

/**
 * <p>
 *   Emits a static-markup tree to a template handler, replacing each maximal run of static events with a single
 *   pre-rendered {@link Text} (flagged as static markup).
 * </p>
 * <p>Element events are kept (never folded into a run) for:</p>
 * <ul>
 *   <li>root-level elements, because fragment inclusion (e.g. {@code th:include}) works on the root elements of the
 *       inserted model;</li>
 *   <li>direct children of elements with processors, because processors may act on child elements
 *       (e.g. {@code th:remove="all-but-first"});</li>
 *   <li>everything inside an element with a model processor, which receives the element's whole model.</li>
 * </ul>
 * <p>
 *   A whitespace-only text ending a run is emitted separately when an element follows, so that iteration
 *   ({@code th:each}) still finds the whitespace preceding the iterated element.
 * </p>
 */
final class StaticRunEmitter {

    private final ITemplateHandler next;
    private final List<ITemplateEvent> run = new ArrayList<ITemplateEvent>();


    StaticRunEmitter(final ITemplateHandler next) {
        super();
        this.next = next;
    }


    void emit(final List<StaticMarkupNode> roots) {
        emitChildren(roots, true);
        flush(false);
    }


    /**
     * @param structural whether child element events must be kept as elements.
     */
    private void emitChildren(final List<StaticMarkupNode> nodes, final boolean structural) {
        for (final StaticMarkupNode node : nodes) {
            if (node.isElement()) {
                emitElement(node, structural);
            } else {
                emitLeaf(node, structural);
            }
        }
    }


    private void emitElement(final StaticMarkupNode node, final boolean structural) {
        if (node.isModelSensitive()) {
            flush(true);
            emitRaw(node);
            return;
        }
        if (node.isStatic() && !structural) {
            node.collectEvents(this.run);
            return;
        }
        flush(true);
        TemplateEventDispatcher.dispatch(node.getEvent(), this.next);
        // Children of an element with processors stay structural; under a static tag they may be folded
        emitChildren(node.getChildren(), !node.hasStaticTag());
        flush(false);
        TemplateEventDispatcher.dispatch(node.getCloseTag(), this.next);
    }


    private void emitLeaf(final StaticMarkupNode node, final boolean structural) {
        final boolean element = node.getEvent() instanceof IStandaloneElementTag;
        if (node.isStatic() && !(structural && element)) {
            this.run.add(node.getEvent());
            return;
        }
        flush(element);
        TemplateEventDispatcher.dispatch(node.getEvent(), this.next);
    }


    private void emitRaw(final StaticMarkupNode node) {
        final List<ITemplateEvent> events = new ArrayList<ITemplateEvent>();
        node.collectEvents(events);
        for (final ITemplateEvent event : events) {
            TemplateEventDispatcher.dispatch(event, this.next);
        }
    }


    /**
     * @param beforeElement whether the next event emitted will be an element.
     */
    private void flush(final boolean beforeElement) {
        final int size = this.run.size();
        if (size == 0) {
            return;
        }
        ITemplateEvent trailingWhitespace = null;
        if (beforeElement && size > 1 && isWhitespaceText(this.run.get(size - 1))) {
            trailingWhitespace = this.run.remove(size - 1);
        }
        if (this.run.size() == 1 && this.run.get(0) instanceof IText) {
            this.next.handleText((IText) this.run.get(0)); // Nothing to merge: keep the original event
        } else {
            this.next.handleText(render(this.run));
        }
        if (trailingWhitespace != null) {
            this.next.handleText((IText) trailingWhitespace);
        }
        this.run.clear();
    }


    private static Text render(final List<ITemplateEvent> events) {
        final FastStringWriter writer = new FastStringWriter(256);
        try {
            for (final ITemplateEvent event : events) {
                event.write(writer);
            }
        } catch (final IOException e) {
            throw new TemplateProcessingException("Error rendering static markup", e); // Cannot happen on a string writer
        }
        final ITemplateEvent first = events.get(0);
        return new Text(writer.toString(), first.getTemplateName(), first.getLine(), first.getCol(), true);
    }


    private static boolean isWhitespaceText(final ITemplateEvent event) {
        return event instanceof IText && WhitespaceCollapsingTemplateHandler.isHtmlWhitespaceOnly((IText) event);
    }

}
