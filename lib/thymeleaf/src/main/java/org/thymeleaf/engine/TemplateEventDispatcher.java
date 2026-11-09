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
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessingInstruction;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEnd;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.model.IText;
import org.thymeleaf.model.IXMLDeclaration;

/**
 * Sends a template event of any type to the matching method of a template handler.
 */
final class TemplateEventDispatcher {

    private TemplateEventDispatcher() {
        super();
    }


    static void dispatch(final ITemplateEvent event, final ITemplateHandler handler) {
        if (event instanceof IText) {
            handler.handleText((IText) event);
        } else if (event instanceof IOpenElementTag) {
            handler.handleOpenElement((IOpenElementTag) event);
        } else if (event instanceof ICloseElementTag) {
            handler.handleCloseElement((ICloseElementTag) event);
        } else if (event instanceof IStandaloneElementTag) {
            handler.handleStandaloneElement((IStandaloneElementTag) event);
        } else if (event instanceof IComment) {
            handler.handleComment((IComment) event);
        } else if (event instanceof ICDATASection) {
            handler.handleCDATASection((ICDATASection) event);
        } else if (event instanceof IDocType) {
            handler.handleDocType((IDocType) event);
        } else if (event instanceof IXMLDeclaration) {
            handler.handleXMLDeclaration((IXMLDeclaration) event);
        } else if (event instanceof IProcessingInstruction) {
            handler.handleProcessingInstruction((IProcessingInstruction) event);
        } else if (event instanceof ITemplateStart) {
            handler.handleTemplateStart((ITemplateStart) event);
        } else if (event instanceof ITemplateEnd) {
            handler.handleTemplateEnd((ITemplateEnd) event);
        } else {
            throw new IllegalArgumentException("Unknown template event type: " + event.getClass().getName());
        }
    }

}
