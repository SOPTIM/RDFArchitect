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
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

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

    /**
     * Drops the redo branch without recording an entry, for a change folded into the current
     * version. What was undone cannot be redone once something has been written over it.
     */
    public void abandonRedo() {
        abandonRedoBranch();
    }

    // -------------------------------------------------------------------------
    // Stepping through history
    // -------------------------------------------------------------------------

    /** Returns whether there is a commit that can be undone. */
    public boolean canUndo() {
        return undoStack.size() > 1;
    }

    /**
     * Returns the commit the next {@link #undo()} would take back, or {@code null} if there is
     * none. Lets a caller see what an undo is about to do before doing it.
     */
    public WorkspaceChangeLogEntry pendingUndo() {
        return canUndo() ? undoStack.peek() : null;
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
        var entry = undoStack.element();
        requireParticipantsAtRecordedVersions(entry);
        undoStack.pop();
        var versions = entry.participants();
        for (int i = versions.size() - 1; i >= 0; i--) {
            versions.get(i).participant().undo();
        }
        redoStack.push(entry);
        return entry;
    }

    /**
     * Refuses to step an entry whose participants have moved on without it.
     *
     * <p>Stepping blind means popping whatever version happens to be on top, so a participant that
     * gained a version outside the workspace transaction would have that version taken back instead
     * of the one the entry names — and the user would see a change they did not ask to undo
     * disappear. Checked before anything is stepped, so a mismatch leaves the log untouched rather
     * than half-walked.
     *
     * <p>A participant may appear more than once in one entry, once per commit that touched it
     * within the transaction. Only the version it gained last is the one it should be standing on,
     * so later entries win. Participants that track no version id are not checked.
     *
     * <p>No such check is needed for a redo: it can only follow an undo, which already ran one.
     */
    private static void requireParticipantsAtRecordedVersions(WorkspaceChangeLogEntry entry) {
        var expected = new LinkedHashMap<ChangeLogParticipant, ParticipantVersion>();
        for (var version : entry.participants()) {
            if (version.versionId() != null) {
                expected.put(version.participant(), version);
            }
        }
        for (var version : expected.values()) {
            if (!version.versionId().equals(version.participant().currentVersionId())) {
                throw new GraphVersionControlException(
                        ("Cannot undo \"%s\": %s has gained a version that no change recorded. "
                                        + "It was committed outside the workspace transaction.")
                                .formatted(entry.message(), version.id()));
            }
        }
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

    /**
     * A recorded change together with everything committed after it.
     *
     * @param change the change that was measured from
     * @param after the commits recorded after it, newest first — what restoring to {@code change}
     *     has to take back
     */
    public record HistorySince(
            WorkspaceChangeLogEntry change, List<WorkspaceChangeLogEntry> after) {}

    /**
     * Returns a recorded change and the commits that followed it. The two are answered together
     * because a restore needs both and the history has to be walked only once to find them.
     *
     * @param changeId the change to measure from
     * @return the change and the commits recorded after it, newest first
     * @throws GraphVersionControlException if no such change is recorded
     */
    public HistorySince historySince(UUID changeId) {
        var after = new ArrayList<WorkspaceChangeLogEntry>();
        for (var entry : undoStack) {
            if (entry.changeId().equals(changeId)) {
                return new HistorySince(entry, List.copyOf(after));
            }
            after.add(entry);
        }
        throw new GraphVersionControlException(
                "Version " + changeId + " not found in the history.");
    }

    /**
     * Collapses the history into the current state, which then becomes the point the workspace
     * started from. Used after loading, where the recorded steps describe how the state was built
     * up rather than anything the user did and could sensibly undo.
     *
     * @param message describes the state that is kept
     */
    public void forgetHistory(String message) {
        abandonRedoBranch();
        while (!undoStack.isEmpty()) {
            var dropped = undoStack.removeLast();
            dropped.participants().forEach(version -> version.participant().discardOldestVersion());
        }
        undoStack.push(WorkspaceChangeLogEntry.of(message, List.of()));
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
