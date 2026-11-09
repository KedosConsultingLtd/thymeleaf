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

public final class Review {

    private final String author;
    private final int rating;
    private final String title;
    private final String body;

    public Review(final String author, final int rating, final String title, final String body) {
        this.author = author;
        this.rating = rating;
        this.title = title;
        this.body = body;
    }

    public String getAuthor() { return this.author; }
    public int getRating() { return this.rating; }
    public String getTitle() { return this.title; }
    public String getBody() { return this.body; }

}
