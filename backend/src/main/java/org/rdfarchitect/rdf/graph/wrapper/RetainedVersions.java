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

import java.util.Deque;

/**
 * The versions a participant keeps, newest first.
 *
 * <p>A participant that holds whole states and one that holds deltas both stack their past versions
 * and are asked for one of them by distance from the newest. Reaching into that stack and refusing
 * a version that has fallen past the retention bound is the same work either way, and lives here so
 * that the two cannot come to answer it differently.
 */
final class RetainedVersions {

    private RetainedVersions() {}

    /**
     * Returns the version standing the given number of steps back from the newest.
     *
     * <p>A version past the retention bound is gone for good, which is a mistake in the caller
     * rather than something to answer with the oldest version still there.
     *
     * @param versions the retained versions, newest first
     * @param versionsBack how far back to reach, {@code 0} being the newest
     * @param <T> what a version is held as
     * @return the version standing there
     * @throws GraphVersionControlException if that version is no longer retained
     */
    static <T> T at(Deque<T> versions, int versionsBack) {
        if (versionsBack < 0 || versionsBack >= versions.size()) {
            throw new GraphVersionControlException(
                    "Cannot reach %d versions back: %d are retained."
                            .formatted(versionsBack, Math.max(versions.size() - 1, 0)));
        }
        var iterator = versions.iterator();
        for (int i = 0; i < versionsBack; i++) {
            iterator.next();
        }
        return iterator.next();
    }
}
