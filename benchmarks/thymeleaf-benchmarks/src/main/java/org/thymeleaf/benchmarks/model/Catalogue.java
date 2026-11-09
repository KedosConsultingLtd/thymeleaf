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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic sample data (fixed seed), so every run and every engine variant renders exactly the same pages.
 */
public final class Catalogue {

    private static final String[] BRANDS = { "Northwind", "Halden", "Atelier Nine", "Copperline", "Fieldnote", "Marlowe" };
    private static final String[] NOUNS = { "Jacket", "Shirt", "Knit", "Trousers", "Dress", "Coat", "Boots", "Scarf" };
    private static final String[] ADJECTIVES = { "Classic", "Relaxed", "Tailored", "Organic", "Waxed", "Merino", "Linen", "Quilted" };
    private static final String[] COLOURS = { "#1f2a44", "#8b5e3c", "#2f6b4f", "#c2b280", "#7a1f2b", "#d9d9d9", "#111111" };
    private static final String LOREM =
            "Cut from a dense, hard-wearing cloth and finished by hand, it is designed to soften with every wear. "
            + "Generous pockets, a relaxed shoulder and reinforced seams make it an everyday piece that lasts for years.";

    public static final int MENU_TOP_LEVEL = 8;
    public static final int MENU_COLUMNS = 5;
    public static final int MENU_LINKS_PER_COLUMN = 6;

    private final List<Category> menu;
    private final List<Product> products;
    private final List<Review> reviews;


    public static Catalogue generate(final int productCount) {
        final Random random = new Random(42L);
        return new Catalogue(menu(), products(random, productCount), reviews(random, 12));
    }


    private Catalogue(final List<Category> menu, final List<Product> products, final List<Review> reviews) {
        this.menu = List.copyOf(menu);
        this.products = List.copyOf(products);
        this.reviews = List.copyOf(reviews);
    }


    public List<Category> getMenu() { return this.menu; }
    public List<Product> getProducts() { return this.products; }
    public List<Review> getReviews() { return this.reviews; }

    public List<Product> firstProducts(final int count) {
        return this.products.subList(0, Math.min(count, this.products.size()));
    }


    private static List<Category> menu() {
        final List<Category> top = new ArrayList<>();
        for (int t = 1; t <= MENU_TOP_LEVEL; t++) {
            final List<Category> columns = new ArrayList<>();
            for (int c = 1; c <= MENU_COLUMNS; c++) {
                final List<Category> links = new ArrayList<>();
                for (int l = 1; l <= MENU_LINKS_PER_COLUMN; l++) {
                    links.add(new Category("Link " + t + "." + c + "." + l, "cat-" + t + "-" + c + "-" + l, List.of()));
                }
                columns.add(new Category("Section " + t + "." + c, "cat-" + t + "-" + c, links));
            }
            top.add(new Category("Department " + t, "cat-" + t, columns));
        }
        return top;
    }


    private static List<Product> products(final Random random, final int count) {
        final List<Product> products = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            final String name = ADJECTIVES[random.nextInt(ADJECTIVES.length)] + " " + NOUNS[random.nextInt(NOUNS.length)];
            final BigDecimal price = BigDecimal.valueOf(1500 + random.nextInt(25000), 2);
            final BigDecimal wasPrice = (random.nextInt(4) == 0)
                    ? price.multiply(BigDecimal.valueOf(1.25)).setScale(2, RoundingMode.HALF_UP) : null;
            final List<String> colours = new ArrayList<>();
            for (int c = 0, n = 1 + random.nextInt(4); c < n; c++) {
                colours.add(COLOURS[(i + c) % COLOURS.length]);
            }
            final List<String> images = new ArrayList<>();
            for (int m = 1; m <= 5; m++) {
                images.add("https://images.example.com/p/" + i + "/" + m + ".jpg");
            }
            products.add(new Product(
                    "P" + (10000 + i), name, name.toLowerCase().replace(' ', '-') + "-" + i,
                    BRANDS[random.nextInt(BRANDS.length)], LOREM, price, wasPrice,
                    1 + random.nextInt(5), random.nextInt(400), random.nextInt(10) != 0, colours, images));
        }
        return products;
    }


    private static List<Review> reviews(final Random random, final int count) {
        final List<Review> reviews = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            reviews.add(new Review("Customer " + i, 1 + random.nextInt(5), "Review title " + i, LOREM));
        }
        return reviews;
    }

}
