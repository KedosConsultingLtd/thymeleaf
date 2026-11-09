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
package org.thymeleaf.benchmarks.model;

import java.math.BigDecimal;
import java.util.List;

public final class Product {

    private final String id;
    private final String name;
    private final String slug;
    private final String brand;
    private final String description;
    private final BigDecimal price;
    private final BigDecimal wasPrice; // null when not on sale
    private final int rating;
    private final int reviewCount;
    private final boolean inStock;
    private final List<String> colours;
    private final List<String> imageUrls;

    public Product(
            final String id, final String name, final String slug, final String brand, final String description,
            final BigDecimal price, final BigDecimal wasPrice, final int rating, final int reviewCount,
            final boolean inStock, final List<String> colours, final List<String> imageUrls) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.brand = brand;
        this.description = description;
        this.price = price;
        this.wasPrice = wasPrice;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.inStock = inStock;
        this.colours = List.copyOf(colours);
        this.imageUrls = List.copyOf(imageUrls);
    }

    public String getId() { return this.id; }
    public String getName() { return this.name; }
    public String getSlug() { return this.slug; }
    public String getBrand() { return this.brand; }
    public String getDescription() { return this.description; }
    public BigDecimal getPrice() { return this.price; }
    public BigDecimal getWasPrice() { return this.wasPrice; }
    public int getRating() { return this.rating; }
    public int getReviewCount() { return this.reviewCount; }
    public boolean isInStock() { return this.inStock; }
    public List<String> getColours() { return this.colours; }
    public List<String> getImageUrls() { return this.imageUrls; }
    public String getImageUrl() { return this.imageUrls.get(0); }

}
