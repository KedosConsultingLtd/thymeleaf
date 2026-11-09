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

import java.io.Writer;
import java.util.Arrays;

/**
 * Reusable output sink that encodes to UTF-8 bytes, standing in for a servlet response: rendering cost includes
 * character encoding, as it does in a web container, without any I/O. Not thread-safe; one per thread.
 */
public final class Utf8Sink extends Writer {

    private byte[] bytes = new byte[256 * 1024];
    private int size = 0;


    public void reset() {
        this.size = 0;
    }

    public int size() {
        return this.size;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(this.bytes, this.size);
    }


    @Override
    public void write(final int c) {
        ensure(3);
        encode((char) c);
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) {
        ensure(len * 3);
        for (int i = off, end = off + len; i < end; i++) {
            encode(cbuf[i]);
        }
    }

    @Override
    public void write(final String str, final int off, final int len) {
        ensure(len * 3);
        for (int i = off, end = off + len; i < end; i++) {
            encode(str.charAt(i));
        }
    }

    @Override
    public void flush() {
        // Nothing to flush
    }

    @Override
    public void close() {
        // Nothing to close
    }


    private void encode(final char c) {
        // Surrogate pairs are encoded as two 3-byte sequences (CESU-8 style): acceptable for a benchmark sink,
        // the templates and data used here are BMP-only.
        if (c < 0x80) {
            this.bytes[this.size++] = (byte) c;
        } else if (c < 0x800) {
            this.bytes[this.size++] = (byte) (0xC0 | (c >> 6));
            this.bytes[this.size++] = (byte) (0x80 | (c & 0x3F));
        } else {
            this.bytes[this.size++] = (byte) (0xE0 | (c >> 12));
            this.bytes[this.size++] = (byte) (0x80 | ((c >> 6) & 0x3F));
            this.bytes[this.size++] = (byte) (0x80 | (c & 0x3F));
        }
    }

    private void ensure(final int extra) {
        if (this.size + extra > this.bytes.length) {
            this.bytes = Arrays.copyOf(this.bytes, Math.max(this.bytes.length * 2, this.size + extra));
        }
    }

}
