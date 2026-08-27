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

    /** The steps a single file goes through, in order. */
    enum Stage {
        PARSING,
        ANALYZING,
        STORING
    }

    /** How the import of a single file ended. */
    enum Outcome {
        IMPORTED,
        FAILED,
        /** Not imported because the import was cancelled before it got to this file. */
        SKIPPED
    }

    /** The files to import are known. Called once, before the first file is started. */
    default void planned(List<PlannedImport> plannedImports) {}

    default void started(int index) {}

    default void stage(int index, Stage stage) {}

    default void finished(int index, Outcome outcome, String graphUri) {}

    /**
     * Whether the import should stop. Checked before each file, so cancelling never leaves a
     * half-imported graph behind.
     */
    default boolean isCancelled() {
        return false;
    }
}
