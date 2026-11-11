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

package org.rdfarchitect.services.archive.parts;

/**
 * One part of the workspace file, such as the schema of a graph or the colours of the workspace.
 * The export writes it, the import first reads and checks it together with every other part, and
 * restores it only once the whole file turned out valid. Keeping the three in one class keeps them
 * in step when the part changes.
 *
 * @param <S> where the part lives: a graph of the workspace or the workspace itself
 * @param <T> what the part holds once read
 */
public interface WorkspaceFilePart<S, T> {

    /** Writes the part, from inside the export's transaction. */
    void write(S source, PartWriter out);

    /**
     * Reads the part and reports to {@code in} whatever keeps it from being restored. Changes
     * nothing, since the import reads every part before it restores the first.
     *
     * @return what the part holds, {@code null} if it reported a problem
     */
    T read(PartReader in);

    /** Restores what {@link #read} returned, once no part of the file reported a problem. */
    void restore(S target, T value);
}
