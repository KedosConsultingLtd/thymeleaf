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
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractAttributeModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Test dialect with a model processor that inspects the element tags inside its element:
 * {@code test:count} appends a comment with the number of element tags in the element's model.
 */
final class CountChildElementsDialect extends AbstractProcessorDialect {

    CountChildElementsDialect() {
        super("CountChildElements", "test", 1000);
    }

    public Set<IProcessor> getProcessors(final String dialectPrefix) {
        return Collections.<IProcessor>singleton(new CountProcessor(dialectPrefix));
    }


    private static final class CountProcessor extends AbstractAttributeModelProcessor {

        CountProcessor(final String dialectPrefix) {
            super(TemplateMode.HTML, dialectPrefix, null, false, "count", true, 1000, true);
        }

        @Override
        protected void doProcess(
                final ITemplateContext context, final IModel model, final AttributeName attributeName,
                final String attributeValue, final IElementModelStructureHandler structureHandler) {
            int count = 0;
            for (int i = 0; i < model.size(); i++) {
                final ITemplateEvent event = model.get(i);
                if (event instanceof IOpenElementTag || event instanceof IStandaloneElementTag) {
                    count++;
                }
            }
            model.add(context.getModelFactory().createComment("elements=" + count));
        }

    }

}
