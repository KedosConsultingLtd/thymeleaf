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
package org.thymeleaf.benchmarks;

import java.util.HashMap;
import java.util.Map;

import org.thymeleaf.benchmarks.model.Catalogue;

/**
 * The pages rendered by the benchmarks. Each one is a full page sharing the same head, header (with a large
 * mega-menu) and footer, like a typical server-rendered storefront.
 */
public enum Scenario {

    /** Landing page: hero, category tiles, 8 featured product cards. */
    HOME("bench/home"),
    /** Product listing: facets, sort, 48 product cards, pagination. */
    LISTING("bench/listing"),
    /** Product detail: gallery, options, accordion, 12 reviews, 4 related product cards. */
    PRODUCT("bench/product"),
    /** Mostly static content page (CMS-like article). */
    CONTENT("bench/content");


    public static final int LISTING_PAGE_SIZE = 48;

    private final String template;

    Scenario(final String template) {
        this.template = template;
    }

    public String getTemplate() {
        return this.template;
    }


    public Map<String, Object> variables(final Catalogue catalogue) {
        final Map<String, Object> variables = new HashMap<>();
        variables.put("menu", catalogue.getMenu());
        variables.put("basketCount", Integer.valueOf(3));
        variables.put("customerName", "Alex");
        switch (this) {
            case HOME:
                variables.put("pageTitle", "Home");
                variables.put("featured", catalogue.firstProducts(8));
                break;
            case LISTING:
                variables.put("pageTitle", "Department 1");
                variables.put("category", catalogue.getMenu().get(0));
                variables.put("products", catalogue.firstProducts(LISTING_PAGE_SIZE));
                variables.put("currentPage", Integer.valueOf(2));
                variables.put("pageCount", Integer.valueOf(10));
                break;
            case PRODUCT:
                variables.put("pageTitle", catalogue.getProducts().get(0).getName());
                variables.put("product", catalogue.getProducts().get(0));
                variables.put("reviews", catalogue.getReviews());
                variables.put("related", catalogue.getProducts().subList(1, 5));
                break;
            case CONTENT:
                variables.put("pageTitle", "Our story");
                break;
            default:
                throw new IllegalStateException("Unknown scenario " + this);
        }
        return variables;
    }

}
