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

import org.thymeleaf.model.ICDATASection;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IComment;
import org.thymeleaf.model.IDocType;
import org.thymeleaf.model.IElementTag;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessingInstruction;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEnd;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.model.IText;
import org.thymeleaf.model.IXMLDeclaration;

/**
 * <p>
 *   Template handler that collapses whitespace-only template text. Intended to run at parse time
 *   (see {@link org.thymeleaf.parsefilter.WhitespaceCollapsingParseFilter}).
 * </p>
 * <p>Rules:</p>
 * <ul>
 *   <li>A text event made only of HTML whitespace (space, tab, LF, CR, FF) is replaced by a single {@code "\n"}.
 *       Text containing any other character, including non-breaking spaces, is left untouched.</li>
 *   <li>Consecutive whitespace-only text events (with no other event between them) produce a single
 *       {@code "\n"}.</li>
 *   <li>Text inside {@code pre}, {@code textarea}, {@code script} and {@code style} elements (at any nesting
 *       depth) is left untouched.</li>
 *   <li>Comments, CDATA sections, DOCTYPEs and all tags are passed through unchanged.</li>
 * </ul>
 * <p>
 *   Limitation: when a template is parsed with markup selectors (fragments), only the selected markup is seen.
 *   A fragment selected from <em>inside</em> a {@code pre}/{@code textarea}/{@code script}/{@code style}
 *   element cannot know about that ancestor, and its whitespace-only text will be collapsed.
 * </p>
 * <p>
 *   Instances are stateful and must be used for a single parse operation only.
 * </p>
 */
public final class WhitespaceCollapsingTemplateHandler extends AbstractTemplateHandler {

    private static final String COLLAPSED = "\n";

    private int preservingDepth = 0;
    private boolean lastWasCollapsedText = false;


    public WhitespaceCollapsingTemplateHandler(final ITemplateHandler next) {
        super(next);
    }


    @Override
    public void handleText(final IText text) {
        if (this.preservingDepth > 0 || !isHtmlWhitespaceOnly(text)) {
            this.lastWasCollapsedText = false;
            super.handleText(text);
            return;
        }
        if (this.lastWasCollapsedText) {
            return; // Merge into the newline already emitted
        }
        this.lastWasCollapsedText = true;
        super.handleText(collapse(text));
    }


    @Override
    public void handleOpenElement(final IOpenElementTag openElementTag) {
        this.lastWasCollapsedText = false;
        if (isPreserving(openElementTag)) {
            this.preservingDepth++;
        }
        super.handleOpenElement(openElementTag);
    }


    @Override
    public void handleCloseElement(final ICloseElementTag closeElementTag) {
        this.lastWasCollapsedText = false;
        if (this.preservingDepth > 0 && !closeElementTag.isUnmatched() && isPreserving(closeElementTag)) {
            this.preservingDepth--;
        }
        super.handleCloseElement(closeElementTag);
    }


    @Override
    public void handleStandaloneElement(final IStandaloneElementTag standaloneElementTag) {
        this.lastWasCollapsedText = false;
        super.handleStandaloneElement(standaloneElementTag);
    }


    @Override
    public void handleComment(final IComment comment) {
        this.lastWasCollapsedText = false;
        super.handleComment(comment);
    }


    @Override
    public void handleCDATASection(final ICDATASection cdataSection) {
        this.lastWasCollapsedText = false;
        super.handleCDATASection(cdataSection);
    }


    @Override
    public void handleDocType(final IDocType docType) {
        this.lastWasCollapsedText = false;
        super.handleDocType(docType);
    }


    @Override
    public void handleXMLDeclaration(final IXMLDeclaration xmlDeclaration) {
        this.lastWasCollapsedText = false;
        super.handleXMLDeclaration(xmlDeclaration);
    }


    @Override
    public void handleProcessingInstruction(final IProcessingInstruction processingInstruction) {
        this.lastWasCollapsedText = false;
        super.handleProcessingInstruction(processingInstruction);
    }


    @Override
    public void handleTemplateStart(final ITemplateStart templateStart) {
        this.lastWasCollapsedText = false;
        this.preservingDepth = 0;
        super.handleTemplateStart(templateStart);
    }


    @Override
    public void handleTemplateEnd(final ITemplateEnd templateEnd) {
        this.lastWasCollapsedText = false;
        super.handleTemplateEnd(templateEnd);
    }


    private static IText collapse(final IText text) {
        if (text.length() == 1 && text.charAt(0) == '\n') {
            return text; // Already collapsed: avoid creating a new event
        }
        return new Text(COLLAPSED, text.getTemplateName(), text.getLine(), text.getCol());
    }


    static boolean isHtmlWhitespaceOnly(final CharSequence text) {
        final int length = text.length();
        if (length == 0) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            final char c = text.charAt(i);
            if (c != ' ' && c != '\n' && c != '\t' && c != '\r' && c != '\f') {
                return false;
            }
        }
        return true;
    }


    static boolean isPreserving(final IElementTag tag) {
        final String name = tag.getElementDefinition().getElementName().getElementName();
        return "pre".equalsIgnoreCase(name)
                || "textarea".equalsIgnoreCase(name)
                || "script".equalsIgnoreCase(name)
                || "style".equalsIgnoreCase(name);
    }

}
