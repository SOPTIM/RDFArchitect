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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One commit in the workspace's history.
 *
 * <p>An entry names every participant that changed, which is what makes a commit spanning several
 * graphs — copying a class from one graph into another, deleting two graphs at once — a single
 * undoable step rather than several. It is also what lets the graph-specific changelog be derived
 * later, without reconstructing anything.
 *
 * @param changeId identifies this entry, e.g. for restoring to a version
 * @param timestamp when the commit happened
 * @param message what the commit did
 * @param participants the participants that gained a version in this commit
 * @param deltas what changed, for display in the changelog; held weakly, so an entry never keeps a
 *     graph alive on its own
 * @param undone whether an undo has stepped over this commit, so that it lies ahead of the
 *     workspace rather than behind it. Answered when the history is read and never while the entry
 *     sits in the log, because it is not a property of the commit but of where the workspace
 *     currently stands relative to it.
 */
public record WorkspaceChangeLogEntry(
        UUID changeId,
        LocalDateTime timestamp,
        String message,
        List<ParticipantVersion> participants,
        List<ContextDelta> deltas,
        boolean undone) {

    public WorkspaceChangeLogEntry {
        participants = List.copyOf(participants);
        deltas = List.copyOf(deltas);
    }

    /**
     * Creates an entry stamped with a fresh id and the current time.
     *
     * @param message what the commit did
     * @param participants the participants that gained a version in this commit
     * @return the entry
     */
    public static WorkspaceChangeLogEntry of(
            String message, List<ParticipantVersion> participants) {
        return of(message, participants, List.of());
    }

    /**
     * Creates an entry stamped with a fresh id and the current time.
     *
     * @param message what the commit did
     * @param participants the participants that gained a version in this commit
     * @param deltas what changed, for display in the changelog
     * @return the entry
     */
    public static WorkspaceChangeLogEntry of(
            String message, List<ParticipantVersion> participants, List<ContextDelta> deltas) {
        return new WorkspaceChangeLogEntry(
                UUID.randomUUID(), LocalDateTime.now(), message, participants, deltas, false);
    }

    /**
     * Returns the same entry, read as one an undo has stepped over.
     *
     * @return the entry
     */
    public WorkspaceChangeLogEntry asUndone() {
        return new WorkspaceChangeLogEntry(
                changeId, timestamp, message, participants, deltas, true);
    }

    /**
     * Returns what undoing this commit would take away — the graphs and diagrams it brought into
     * existence. Empty for a commit that only changed things that already existed, which is what
     * lets the editor ask before an undo that makes something disappear and stay out of the way
     * otherwise.
     */
    public List<String> removedOnUndo() {
        return participants.stream().flatMap(version -> version.additions().stream()).toList();
    }

    /**
     * Returns what kinds of data this commit touched — the schema, the SHACL shapes, the layout —
     * so that a message about it can say where the change landed and the changelog can be filtered
     * down to one of them. Named by {@link ParticipantId#kindName()}, which is also what {@link
     * ContextDelta#contextName()} answers, so that the two can be filtered on alike; a commit that
     * changed only data without a delta, such as deleting a graph, is still covered by this.
     */
    public List<String> affectedKinds() {
        return participants.stream().map(version -> version.id().kindName()).distinct().toList();
    }

    /**
     * Returns the URIs of the graphs whose own contents this commit changed, which is what a
     * restore held to a graph can put back.
     *
     * <p>Narrower than {@link #affectedGraphUris()} by exactly what {@link RevertScope#covers}
     * leaves alone: a graph a workspace-scoped participant merely named is shown in that graph's
     * changelog but is not restorable there, because the set of graphs belongs to the workspace.
     */
    public Set<String> restorableGraphUris() {
        return participants.stream()
                .map(ParticipantVersion::id)
                .filter(ParticipantId::isGraphScoped)
                .map(ParticipantId::scope)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the URIs of the graphs this commit touched.
     *
     * <p>Includes the graphs a workspace-scoped participant named: creating, deleting and renaming
     * a graph is recorded on the set of graphs rather than on the graph itself, and leaving those
     * out would mean a graph's own changelog never shows that it came into existence.
     */
    public Set<String> affectedGraphUris() {
        return participants.stream()
                .flatMap(
                        version ->
                                Stream.concat(
                                        version.id().isGraphScoped()
                                                ? Stream.of(version.id().scope())
                                                : Stream.empty(),
                                        version.affectedGraphs().stream()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
