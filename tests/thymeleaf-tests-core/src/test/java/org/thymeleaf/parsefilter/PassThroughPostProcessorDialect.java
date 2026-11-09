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

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Test dialect registering a post-processor that changes nothing (its presence must disable coalescing).
 */
final class PassThroughPostProcessorDialect extends AbstractDialect implements IPostProcessorDialect {

    PassThroughPostProcessorDialect() {
        super("PassThroughPostProcessor");
    }

    public int getDialectPostProcessorPrecedence() {
        return 1000;
    }

    public Set<IPostProcessor> getPostProcessors() {
        return Collections.<IPostProcessor>singleton(
                new PostProcessor(TemplateMode.HTML, PassThroughHandler.class, 1000));
    }

    public static final class PassThroughHandler extends AbstractTemplateHandler {
        public PassThroughHandler() {
            super();
        }
    }

}
