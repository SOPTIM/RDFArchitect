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

package org.rdfarchitect.services.shacl.validation;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The stored documents an unsaved buffer is compared against, per session and workspace.
 *
 * <p>The editor asks for a comparison on every debounced keystroke, and answering it copies every
 * enabled shapes document of the workspace — tens of thousands of triples for a full ENTSO-E set —
 * to compare them with a buffer that is the only thing that moved. Every commit, undo or redo that
 * touches a document moves the workspace's documents version, so an entry is good while that
 * version stays, renaming or switching a document off included, without the write path having to
 * know this exists.
 *
 * <p>Kept per session, because the in-memory database is per session and two users typing at once
 * would otherwise replace each other's copies on every request. Released like {@link
 * SchemaIndexCache}'s indexes: sessions end without telling anyone, so an entry nobody has asked
 * for in a while goes, and a count bound covers the rest.
 */
final class ComparisonCache {

    private static final Duration DEFAULT_MAX_IDLE = Duration.ofMinutes(30);

    /** One entry per session typing; more than this many at once is not what the bound is for. */
    private static final int DEFAULT_MAX_ENTRIES = 16;

    record Key(String sessionId, String workspaceName) {}

    private record Entry(
            UUID version, List<ShapesConflictAnalyzer.Document> documents, long lastUsedMillis) {}

    private final Duration maxIdle;

    private final int maxEntries;

    /** Wall clock, in milliseconds. Injected so the idle bound can be tested without waiting. */
    private final LongSupplier clock;

    /** Access-ordered, so the eldest entry is the least recently used. */
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    ComparisonCache() {
        this(DEFAULT_MAX_IDLE, DEFAULT_MAX_ENTRIES, System::currentTimeMillis);
    }

    ComparisonCache(Duration maxIdle, int maxEntries, LongSupplier clock) {
        this.maxIdle = maxIdle;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    /** The documents held for {@code key} at {@code version}, or null when none are. */
    synchronized List<ShapesConflictAnalyzer.Document> get(Key key, UUID version) {
        long now = clock.getAsLong();
        evictIdle(now);
        var entry = entries.get(key);
        if (entry == null || !entry.version().equals(version)) {
            return null;
        }
        entries.put(key, new Entry(version, entry.documents(), now));
        return entry.documents();
    }

    synchronized void put(Key key, UUID version, List<ShapesConflictAnalyzer.Document> documents) {
        long now = clock.getAsLong();
        evictIdle(now);
        entries.put(key, new Entry(version, documents, now));
        var eldest = entries.keySet().iterator();
        while (entries.size() > maxEntries) {
            eldest.next();
            eldest.remove();
        }
    }

    synchronized int size() {
        evictIdle(clock.getAsLong());
        return entries.size();
    }

    private void evictIdle(long now) {
        entries.values().removeIf(entry -> now - entry.lastUsedMillis() > maxIdle.toMillis());
    }
}
