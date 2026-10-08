/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.database;

import org.rdfarchitect.exception.database.DataAccessException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A document's text as its history keeps it: deflated.
 *
 * <p>Every version of a constraints document holds the text it was saved as, and the history is
 * {@code maxVersions} deep. Kept as strings, a session editing an official 3 MB file grew by the
 * file's size on every save; Turtle compresses about tenfold, so this is what makes a deep history
 * of such a file affordable. Equality is by content, which the history relies on to tell whether a
 * transaction changed anything.
 */
public final class StoredText {

    private final byte[] deflated;
    private final int length;

    private StoredText(byte[] deflated, int length) {
        this.deflated = deflated;
        this.length = length;
    }

    /** The text stored, or {@code null} for {@code null}. */
    public static StoredText of(String text) {
        if (text == null) {
            return null;
        }
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        var deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(bytes);
            deflater.finish();
            var out = new ByteArrayOutputStream(Math.max(64, bytes.length / 8));
            var buffer = new byte[64 * 1024];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return new StoredText(out.toByteArray(), bytes.length);
        } finally {
            deflater.end();
        }
    }

    /** The text, inflated anew on every call — callers that read it often should keep it. */
    public String text() {
        var inflater = new Inflater();
        try {
            inflater.setInput(deflated);
            var bytes = new byte[length];
            int read = 0;
            while (read < length) {
                int n = inflater.inflate(bytes, read, length - read);
                if (n == 0 && (inflater.finished() || inflater.needsInput())) {
                    break;
                }
                read += n;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            throw new DataAccessException("Stored constraints text is corrupt", e);
        } finally {
            inflater.end();
        }
    }

    /** Bytes held, for tests and diagnostics. */
    int storedSize() {
        return deflated.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StoredText that
                && length == that.length
                && Arrays.equals(deflated, that.deflated);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(deflated) + length;
    }
}
