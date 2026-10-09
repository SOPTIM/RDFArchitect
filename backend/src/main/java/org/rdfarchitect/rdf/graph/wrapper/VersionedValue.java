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

package org.rdfarchitect.rdf.graph.wrapper;

import org.rdfarchitect.exception.graph.GraphVersionControlException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A single immutable value that takes part in a context's transactions and history, the way an
 * {@link RDFGraphDelta} does for triples.
 *
 * <p>It keeps the same version counter as a graph participant — one version per commit, trimmed by
 * the same rule — so a context can undo it the same number of times as its graphs. Like the graphs
 * it has no lock or transaction check of its own; the owning context provides both.
 *
 * @param <T> the value type; must be immutable, since versions share instances
 */
public class VersionedValue<T> implements TransactionParticipant {

    private final Deque<T> past = new ArrayDeque<>();
    private final Deque<T> future = new ArrayDeque<>();
    private volatile T current;

    private final int maxVersions;
    private final int compressCount;

    public VersionedValue(T initial, int maxVersions, int compressCount) {
        this.maxVersions = maxVersions;
        this.compressCount = compressCount;
        past.push(Objects.requireNonNull(initial));
        current = initial;
    }

    /** The value as the running transaction sees it; outside one, the committed value. */
    public T get() {
        return current;
    }

    /** The value as last committed, whatever the running transaction has set since. */
    public T committed() {
        return past.getFirst();
    }

    public void set(T value) {
        current = Objects.requireNonNull(value);
    }

    @Override
    public void commit() {
        past.push(current);
        future.clear();
        if (past.size() > maxVersions) {
            int deleteCount = Math.min(past.size() - 1, compressCount);
            for (int i = 0; i < deleteCount; i++) {
                past.removeLast();
            }
        }
    }

    @Override
    public void abort() {
        current = past.getFirst();
    }

    @Override
    public boolean hasChanges() {
        return !Objects.equals(current, past.getFirst());
    }

    public void undo() {
        if (past.size() < 2) {
            throw new GraphVersionControlException("Cannot undo: already at the oldest version.");
        }
        future.push(past.pop());
        current = past.getFirst();
    }

    public void redo() {
        if (future.isEmpty()) {
            throw new GraphVersionControlException("Cannot redo: already at the newest version.");
        }
        past.push(future.pop());
        current = past.getFirst();
    }

    /** Counts versions the way {@link RDFGraphDelta#currentVersion()} does. */
    public int currentVersion() {
        return past.size() - 1;
    }

    /**
     * Repeats the committed value until the history is {@code targetVersion} deep, for a value
     * joining a context that already has a history — see {@link RDFGraphDelta#padHistory(int)}.
     */
    public void padHistory(int targetVersion) {
        while (currentVersion() < targetVersion) {
            past.push(past.getFirst());
        }
    }

    /** Every value still reachable: the current one and every version undo or redo can reach. */
    public Stream<T> reachableValues() {
        return Stream.concat(Stream.of(current), Stream.concat(past.stream(), future.stream()));
    }
}
