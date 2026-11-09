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
import java.util.Collections;
import java.util.List;

import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.ITemplateEvent;

/**
 * <p>
 *   A node of the element tree built at parse time for static-run coalescing: either an element
 *   (open tag, children, close tag) or a single leaf event.
 * </p>
 */
final class StaticMarkupNode {

    private final ITemplateEvent event;
    private List<StaticMarkupNode> children;
    private ICloseElementTag closeTag;
    private boolean staticNode;
    private boolean staticTag;
    private boolean modelSensitive;


    static StaticMarkupNode leaf(final ITemplateEvent event, final boolean staticLeaf) {
        final StaticMarkupNode node = new StaticMarkupNode(event);
        node.staticNode = staticLeaf;
        return node;
    }

    static StaticMarkupNode element(final IOpenElementTag openTag) {
        return new StaticMarkupNode(openTag);
    }


    private StaticMarkupNode(final ITemplateEvent event) {
        super();
        this.event = event;
    }


    void addChild(final StaticMarkupNode child) {
        if (this.children == null) {
            this.children = new ArrayList<StaticMarkupNode>(4);
        }
        this.children.add(child);
    }


    /**
     * Complete an element node once its close tag has been parsed.
     */
    void close(final ICloseElementTag closeTag, final boolean staticTag, final boolean modelSensitive) {
        this.closeTag = closeTag;
        this.staticTag = staticTag;
        this.modelSensitive = modelSensitive;
        this.staticNode = staticTag && allChildrenStatic();
    }


    private boolean allChildrenStatic() {
        if (this.children != null) {
            for (final StaticMarkupNode child : this.children) {
                if (!child.staticNode) {
                    return false;
                }
            }
        }
        return true;
    }


    boolean isElement() {
        return this.event instanceof IOpenElementTag;
    }

    boolean isStatic() {
        return this.staticNode;
    }

    /**
     * @return true if this element's own tag has no processors (its subtree may still contain dynamic nodes).
     */
    boolean hasStaticTag() {
        return this.staticTag;
    }

    boolean isModelSensitive() {
        return this.modelSensitive;
    }

    ITemplateEvent getEvent() {
        return this.event;
    }

    ICloseElementTag getCloseTag() {
        return this.closeTag;
    }

    List<StaticMarkupNode> getChildren() {
        return (this.children == null ? Collections.<StaticMarkupNode>emptyList() : this.children);
    }


    /**
     * Append every event of this node's subtree, in document order.
     */
    void collectEvents(final List<ITemplateEvent> target) {
        target.add(this.event);
        if (this.children != null) {
            for (final StaticMarkupNode child : this.children) {
                child.collectEvents(target);
            }
        }
        if (this.closeTag != null) {
            target.add(this.closeTag);
        }
    }

}
