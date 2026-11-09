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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads the benchmark message bundle ({@code templates/bench/messages.properties}).
 */
final class Messages {

    private static final String BUNDLE = "templates/bench/messages.properties";

    private Messages() {
    }

    static Map<String, String> load() {
        final Properties properties = new Properties();
        try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(BUNDLE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + BUNDLE);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot read " + BUNDLE, e);
        }
        final Map<String, String> messages = new HashMap<>();
        for (final String key : properties.stringPropertyNames()) {
            messages.put(key, properties.getProperty(key));
        }
        return messages;
    }

}
