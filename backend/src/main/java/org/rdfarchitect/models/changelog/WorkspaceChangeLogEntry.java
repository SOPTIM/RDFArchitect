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
 */
public record WorkspaceChangeLogEntry(
        UUID changeId,
        LocalDateTime timestamp,
        String message,
        List<ParticipantVersion> participants,
        List<ContextDelta> deltas) {

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
                UUID.randomUUID(), LocalDateTime.now(), message, participants, deltas);
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
     * so that a message about it can say where the change landed.
     */
    public List<String> affectedKinds() {
        return participants.stream().map(version -> version.id().kind().name()).distinct().toList();
    }

    /** Returns the URIs of the graphs this commit touched. */
    public Set<String> affectedGraphUris() {
        return participants.stream()
                .map(ParticipantVersion::id)
                .filter(ParticipantId::isGraphScoped)
                .map(ParticipantId::scope)
                .collect(Collectors.toUnmodifiableSet());
    }
}
