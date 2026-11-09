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
package org.thymeleaf.parsefilter;

import java.util.Collections;
import java.util.Set;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Test dialect with a tag processor that parses a runtime string (model data) through IModelFactory.parse, the way
 * sanitising or rewriting dialects do: {@code test:parse="${html}"} replaces the element body with one line per
 * parsed event ("TypeName:text"), so tests can see exactly what the parse returned.
 */
final class ModelParseDialect extends AbstractProcessorDialect {

    ModelParseDialect() {
        super("ModelParse", "test", 1000);
    }

    public Set<IProcessor> getProcessors(final String dialectPrefix) {
        return Collections.<IProcessor>singleton(new ParseProcessor(dialectPrefix));
    }


    private static final class ParseProcessor extends AbstractAttributeTagProcessor {

        ParseProcessor(final String dialectPrefix) {
            super(TemplateMode.HTML, dialectPrefix, null, false, "parse", true, 1000, true);
        }

        @Override
        protected void doProcess(
                final ITemplateContext context, final IProcessableElementTag tag, final AttributeName attributeName,
                final String attributeValue, final IElementTagStructureHandler structureHandler) {
            final String html = (String) context.getVariable(attributeValue);
            final IModel model = context.getModelFactory().parse(context.getTemplateData(), html);
            final StringBuilder dump = new StringBuilder();
            for (int i = 0; i < model.size(); i++) {
                final ITemplateEvent event = model.get(i);
                dump.append(event.getClass().getSimpleName()).append(':').append(event.toString().replace("\n", "\\n")).append('|');
            }
            structureHandler.setBody(dump.toString(), false);
        }

    }

}
