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

package org.rdfarchitect.models.changelog;

/**
 * A component whose version history is driven by the {@link WorkspaceChangeLog}.
 *
 * <p>A participant holds its own versions — for a graph that is unavoidable, because a {@link
 * org.rdfarchitect.rdf.graph.DeltaCompressible} chain <em>is</em> the graph's current state. The
 * decision of when to move through them belongs to the log, which owns the single undo/redo stack
 * of the workspace and tells participants when to step, when their oldest version has fallen past
 * the history horizon, and when a redo branch has been abandoned.
 */
public interface ChangeLogParticipant {

    /** Steps back to the previous version. */
    void undo();

    /** Steps forward to the version a previous {@link #undo()} moved out of the way. */
    void redo();

    /**
     * Folds the oldest retained version into the base, giving up the ability to step back past it.
     * Called when the log drops the entry that created that version.
     */
    void discardOldestVersion();

    /**
     * Drops all versions that {@link #undo()} moved out of the way. Called when a new commit
     * invalidates the redo branch, so that the abandoned versions do not stay reachable.
     */
    void discardRedoHistory();

    /**
     * Merges the version just committed into the one before it, leaving the participant with the
     * new content but the same number of versions.
     *
     * <p>Used for a change the user did not make and cannot mean to undo — the layout a diagram is
     * given the first time it is opened. Recording it as a version while the log ignored it would
     * put the two out of step, which is the one thing the log must never allow.
     */
    void foldLastVersionIntoPrevious();
}
