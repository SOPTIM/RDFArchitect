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

import java.util.UUID;

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

    /**
     * Returns the id of the version this participant is standing on, or {@code null} if it does not
     * track version ids.
     *
     * <p>Lets the log check that the two still agree on where the participant is before it steps
     * it. They can only disagree if something committed the participant outside the workspace
     * transaction, and the log would then undo a change other than the one its entry names.
     */
    default UUID currentVersionId() {
        return versionIdAt(0);
    }

    /**
     * Returns the id of the version this participant gained {@code versionsBack} versions ago, or
     * {@code null} if it does not track version ids.
     *
     * <p>Lets a caller that worked out how far back a state lies check that answer against the
     * participant's own chain before acting on it.
     *
     * @param versionsBack how far back to look; {@code 0} is the version it is standing on
     * @return the version's id
     */
    default UUID versionIdAt(int versionsBack) {
        return null;
    }

    /**
     * Takes a state this participant was in, so that it can be written back as a change of its own.
     *
     * <p>That is what makes a restore a new commit rather than a rewind: nothing is stepped, the
     * old state is read where it already lies and written forward. History then only ever grows in
     * one direction, the restore can itself be undone, and a restore covering part of the workspace
     * never touches the rest.
     *
     * @param versionsBack how far back the state lies; {@code 0} is the current state
     * @return what writes the captured state back, to be run inside a write transaction on the
     *     owning workspace
     * @throws org.rdfarchitect.exception.graph.GraphVersionControlException if the participant does
     *     not reach that far back
     */
    CapturedState capture(int versionsBack);

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
