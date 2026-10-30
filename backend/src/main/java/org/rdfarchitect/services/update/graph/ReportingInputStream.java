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

package org.rdfarchitect.services.update.graph;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongConsumer;

/**
 * An input stream that says how much of itself has been read.
 *
 * <p>For the steps that are one long pass over a file: reading a 131 MB export takes seconds and
 * reports nothing of its own, so a progress built from steps alone can only guess at it. This turns
 * the guess into the one number that is actually known.
 *
 * <p>The report is throttled to whole percents of the expected total. Reading happens in buffers of
 * a few kilobytes, and a listener behind a synchronized job would otherwise be called tens of
 * thousands of times to say the same thing.
 */
final class ReportingInputStream extends FilterInputStream {

    private final long total;
    private final LongConsumer report;
    private long done;
    private long reportedAt;

    private ReportingInputStream(InputStream in, long total, LongConsumer report) {
        super(in);
        this.total = total;
        this.report = report;
    }

    /**
     * Wraps {@code in}, or returns it unchanged when there is nothing to report against.
     *
     * @param total how many bytes are expected, {@code -1} when that is not known
     * @param report receives the bytes read so far
     */
    static InputStream of(InputStream in, long total, LongConsumer report) {
        return total > 0 ? new ReportingInputStream(in, total, report) : in;
    }

    @Override
    public int read() throws IOException {
        var value = in.read();
        if (value >= 0) {
            advance(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        var count = in.read(buffer, offset, length);
        if (count > 0) {
            advance(count);
        }
        return count;
    }

    @Override
    public long skip(long count) throws IOException {
        var skipped = in.skip(count);
        advance(skipped);
        return skipped;
    }

    private void advance(long count) {
        done += count;
        if (done - reportedAt < total / 100 && done < total) {
            return;
        }
        reportedAt = done;
        report.accept(done);
    }
}
