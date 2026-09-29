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
 */
public record WorkspaceChangeLogEntry(
        UUID changeId,
        LocalDateTime timestamp,
        String message,
        List<ParticipantVersion> participants) {

    public WorkspaceChangeLogEntry {
        participants = List.copyOf(participants);
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
        return new WorkspaceChangeLogEntry(
                UUID.randomUUID(), LocalDateTime.now(), message, participants);
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
