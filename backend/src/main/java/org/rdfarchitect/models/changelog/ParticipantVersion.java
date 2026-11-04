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

import java.util.List;
import java.util.UUID;

/**
 * The version a single participant gained in one commit.
 *
 * <p>Carries both the live participant, so that the log can step it, and its descriptive {@link
 * ParticipantId}, so that an entry can be rendered without touching the participant. The direct
 * reference is deliberate: a lookup by identifier would go stale when a graph is renamed.
 *
 * @param id describes which part of the workspace the participant represents
 * @param participant the participant itself
 * @param versionId the version the participant gained in this commit
 * @param additions what this version brought into existence and undoing it would remove again,
 *     recorded here rather than asked of the participant later, which would answer for its newest
 *     version instead of this one
 */
public record ParticipantVersion(
        ParticipantId id,
        ChangeLogParticipant participant,
        UUID versionId,
        List<String> additions) {

    public ParticipantVersion {
        additions = List.copyOf(additions);
    }

    /**
     * Creates a version that brought nothing new into existence.
     *
     * @param id describes which part of the workspace the participant represents
     * @param participant the participant itself
     * @param versionId the version the participant gained in this commit
     * @return the version
     */
    public static ParticipantVersion of(
            ParticipantId id, ChangeLogParticipant participant, UUID versionId) {
        return new ParticipantVersion(id, participant, versionId, List.of());
    }
}
