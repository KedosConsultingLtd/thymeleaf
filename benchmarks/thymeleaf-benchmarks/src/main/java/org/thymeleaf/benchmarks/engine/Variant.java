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
package org.thymeleaf.benchmarks.engine;

import java.util.List;

import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.parsefilter.StaticRunCoalescingDialect;
import org.thymeleaf.parsefilter.WhitespaceCollapsingDialect;

/**
 * Engine optimisation variants compared by the benchmarks.
 */
public enum Variant {

    /** Stock engine. */
    BASELINE(false, false),
    /** Parse-time whitespace collapsing (output differs only in whitespace). */
    WHITESPACE(true, false),
    /** Parse-time static-run coalescing (output byte-identical to BASELINE). */
    COALESCING(false, true),
    /** Both (output identical to WHITESPACE). */
    WHITESPACE_AND_COALESCING(true, true);


    private final boolean whitespace;
    private final boolean coalescing;

    Variant(final boolean whitespace, final boolean coalescing) {
        this.whitespace = whitespace;
        this.coalescing = coalescing;
    }

    public List<IDialect> extraDialects() {
        if (this.whitespace && this.coalescing) {
            return List.of(new WhitespaceCollapsingDialect(), new StaticRunCoalescingDialect());
        }
        if (this.whitespace) {
            return List.of(new WhitespaceCollapsingDialect());
        }
        if (this.coalescing) {
            return List.of(new StaticRunCoalescingDialect());
        }
        return List.of();
    }

    /**
     * @return the variant whose output this one must reproduce byte for byte.
     */
    public Variant referenceOutput() {
        return (this.whitespace ? WHITESPACE : BASELINE);
    }

}
