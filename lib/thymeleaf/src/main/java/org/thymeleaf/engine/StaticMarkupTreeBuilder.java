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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.ITemplateEvent;

/**
 * <p>
 *   Builds the element tree of one parsed template (or fragment) and computes which nodes are static.
 * </p>
 */
final class StaticMarkupTreeBuilder {

    /*
     * The tree is processed recursively. Templates nested deeper than this are passed through unchanged instead
     * of risking a StackOverflowError (real templates are nowhere near this depth).
     */
    static final int MAX_DEPTH = 256;

    private StaticMarkupTreeBuilder() {
        super();
    }


    /**
     * @return the root nodes, or {@code null} if the events are not balanced or nest deeper than {@link #MAX_DEPTH}
     *         (in which case coalescing must not be attempted).
     */
    static List<StaticMarkupNode> build(final List<ITemplateEvent> events, final StaticMarkupPolicy policy) {

        final List<StaticMarkupNode> roots = new ArrayList<StaticMarkupNode>();
        final Deque<StaticMarkupNode> open = new ArrayDeque<StaticMarkupNode>();

        for (final ITemplateEvent event : events) {
            if (event instanceof IOpenElementTag) {
                final StaticMarkupNode node = StaticMarkupNode.element((IOpenElementTag) event);
                addTo(roots, open, node);
                open.push(node);
                if (open.size() > MAX_DEPTH) {
                    return null;
                }
            } else if (event instanceof ICloseElementTag && !((ICloseElementTag) event).isUnmatched()) {
                if (open.isEmpty()) {
                    return null;
                }
                final StaticMarkupNode node = open.pop();
                final IOpenElementTag openTag = (IOpenElementTag) node.getEvent();
                node.close((ICloseElementTag) event, policy.isStaticTag(openTag), policy.isModelSensitive(openTag));
            } else {
                addTo(roots, open, StaticMarkupNode.leaf(event, policy.isStaticLeaf(event)));
            }
        }

        return (open.isEmpty() ? roots : null);

    }


    private static void addTo(
            final List<StaticMarkupNode> roots, final Deque<StaticMarkupNode> open, final StaticMarkupNode node) {
        if (open.isEmpty()) {
            roots.add(node);
        } else {
            open.peek().addChild(node);
        }
    }

}
