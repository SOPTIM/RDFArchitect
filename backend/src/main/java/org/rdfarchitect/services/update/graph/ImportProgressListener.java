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

import java.util.List;

/**
 * Receives the progress of a running import. An import expands the uploaded files into the graph
 * files it will actually import (a zip archive contributes one entry per graph file it contains)
 * and reports that plan first, so that a caller can show every file before the work on it starts.
 * Each planned file is then addressed by its index in that plan.
 *
 * <p>All methods are optional; {@link #NOOP} is an implementation that ignores everything and never
 * cancels.
 */
public interface ImportProgressListener {

    ImportProgressListener NOOP = new ImportProgressListener() {};

    /**
     * A graph file the import is going to process.
     *
     * @param index position of the file within the plan, used to address it in the other callbacks
     * @param fileName name of the file, for a zip entry the name of the entry
     * @param sizeBytes size of the file, or {@code -1} when the archive does not declare it
     */
    record PlannedImport(int index, String fileName, long sizeBytes) {}

    /** How the import of a single file ended. */
    enum Outcome {
        IMPORTED,
        FAILED,
        /** Not imported because the import was cancelled before it got to this file. */
        SKIPPED
    }

    /** The files to import are known. Called once, before the first file is started. */
    default void planned(List<PlannedImport> plannedImports) {}

    /**
     * The import is reading the namespace prefixes of the planned files to find out whether any of
     * them collide. Called once, after {@link #planned(List)} and before the first file is stored.
     */
    default void scanningPrefixes() {}

    /**
     * Asks what to do with the prefixes of the import once at least one of them is contested. The
     * import blocks on this call, so an implementation that lets a user decide may take as long as
     * it needs; it only has to return, or report itself {@link #isCancelled() cancelled}, for the
     * import to go on.
     *
     * @param comparison every prefix of the dataset and of the import, contested ones marked
     * @return what to do with each prefix; a binding without an answer keeps its default, which
     *     leaves the prefixes of the dataset as they are
     */
    default PrefixResolutions awaitPrefixResolutions(List<PrefixComparison> comparison) {
        return PrefixResolutions.none();
    }

    default void started(int index) {}

    default void finished(int index, Outcome outcome, String graphUri) {}

    /**
     * Whether the import should stop. Checked before each file, so cancelling never leaves a
     * half-imported graph behind.
     */
    default boolean isCancelled() {
        return false;
    }
}
