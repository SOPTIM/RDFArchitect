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

import org.rdfarchitect.exception.graph.GraphVersionControlException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The single undo/redo stack of a workspace.
 *
 * <p>The log is the only authority on what can be undone. Participants hold their own versions,
 * because for a graph the version chain is its current state, but every step through them is driven
 * from here. That is what keeps the log from promising an undo a participant cannot perform.
 *
 * <p>Three invariants keep the log and its participants in step:
 *
 * <ol>
 *   <li>A participant appears in an entry exactly when it gained a version in that commit, which
 *       follows from participants enrolling themselves on their first write.
 *   <li>The log decides when history falls off the end. Dropping the oldest entry folds the
 *       corresponding version of each participant named in it into its base.
 *   <li>Abandoning the redo branch tells the affected participants to forget the versions that undo
 *       moved out of the way, so they are not retained for a redo that can never happen.
 * </ol>
 *
 * <p>An entry may name the same participant more than once, because a commit that does not name
 * itself contributes its versions to the next entry. Undo therefore walks an entry's versions
 * backwards and redo walks them forwards.
 *
 * <p><strong>Thread safety:</strong> not thread-safe on its own; the owning workspace synchronises
 * access.
 */
public class WorkspaceChangeLog {

    private final Deque<WorkspaceChangeLogEntry> undoStack = new ArrayDeque<>();
    private final Deque<WorkspaceChangeLogEntry> redoStack = new ArrayDeque<>();

    private final int maxEntries;

    /**
     * Creates a log holding nothing but the state the workspace was loaded with, which cannot be
     * undone.
     *
     * @param initialMessage describes the loaded state, e.g. {@code "imported graphs"}
     * @param maxEntries how many entries to retain, the initial entry included; older entries are
     *     folded into the participants' base state
     */
    public WorkspaceChangeLog(String initialMessage, int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("A changelog must retain at least one entry.");
        }
        this.maxEntries = maxEntries;
        undoStack.push(WorkspaceChangeLogEntry.of(initialMessage, List.of()));
    }

    // -------------------------------------------------------------------------
    // Recording commits
    // -------------------------------------------------------------------------

    /**
     * Records a commit as a new entry, abandoning any redo branch.
     *
     * @param entry the commit to record
     */
    public void push(WorkspaceChangeLogEntry entry) {
        abandonRedoBranch();
        undoStack.push(entry);
        trimToMaxEntries();
    }

    // -------------------------------------------------------------------------
    // Stepping through history
    // -------------------------------------------------------------------------

    /** Returns whether there is a commit that can be undone. */
    public boolean canUndo() {
        return undoStack.size() > 1;
    }

    /** Returns whether there is an undone commit that can be reapplied. */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Steps every participant of the most recent commit back and moves the entry onto the redo
     * stack.
     *
     * @return the entry that was undone
     * @throws GraphVersionControlException if there is nothing to undo
     */
    public WorkspaceChangeLogEntry undo() {
        if (!canUndo()) {
            throw new GraphVersionControlException("Cannot undo: no history available.");
        }
        var entry = undoStack.pop();
        var versions = entry.participants();
        for (int i = versions.size() - 1; i >= 0; i--) {
            versions.get(i).participant().undo();
        }
        redoStack.push(entry);
        return entry;
    }

    /**
     * Steps every participant of the most recently undone commit forward again and moves the entry
     * back onto the undo stack.
     *
     * @return the entry that was redone
     * @throws GraphVersionControlException if there is nothing to redo
     */
    public WorkspaceChangeLogEntry redo() {
        if (!canRedo()) {
            throw new GraphVersionControlException("Cannot redo: no future history available.");
        }
        var entry = redoStack.pop();
        entry.participants().forEach(version -> version.participant().redo());
        undoStack.push(entry);
        return entry;
    }

    // -------------------------------------------------------------------------
    // Reading history
    // -------------------------------------------------------------------------

    /** Returns the recorded commits, newest first. */
    public List<WorkspaceChangeLogEntry> undoHistory() {
        return List.copyOf(undoStack);
    }

    /** Returns the undone commits, the one that would be redone next first. */
    public List<WorkspaceChangeLogEntry> redoHistory() {
        return List.copyOf(redoStack);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void abandonRedoBranch() {
        if (redoStack.isEmpty()) {
            return;
        }
        distinctParticipantsOf(redoStack).forEach(ChangeLogParticipant::discardRedoHistory);
        redoStack.clear();
    }

    private void trimToMaxEntries() {
        while (undoStack.size() > maxEntries) {
            var dropped = undoStack.removeLast();
            dropped.participants().forEach(version -> version.participant().discardOldestVersion());
        }
    }

    private static LinkedHashSet<ChangeLogParticipant> distinctParticipantsOf(
            Deque<WorkspaceChangeLogEntry> entries) {
        var participants = new LinkedHashSet<ChangeLogParticipant>();
        entries.forEach(
                entry ->
                        entry.participants()
                                .forEach(version -> participants.add(version.participant())));
        return participants;
    }
}
