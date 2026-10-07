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

import org.apache.jena.graph.Graph;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * What one participant changed in a commit, for display in the changelog.
 *
 * <p>Carries the full {@link ParticipantId} rather than only the kind of data, so that a commit
 * touching the same kind in two graphs can still say which delta belongs to which graph.
 *
 * <p>A participant shows itself either as triples or as named values, never as both: a graph has a
 * delta chain, while prefixes and colours are kept whole. The unused side is left empty rather than
 * given a second delta type, so that everything the changelog can open up is reached the same way.
 *
 * @param participant which part of the workspace changed
 * @param additions the triples the commit added, held weakly so that an entry never keeps a graph
 *     alive on its own, or {@code null} for a participant that does not hold triples
 * @param deletions the triples the commit removed, held weakly for the same reason, or {@code null}
 *     for a participant that does not hold triples
 * @param values the named values the commit changed, empty for a participant that holds triples
 */
public record ContextDelta(
        ParticipantId participant,
        WeakReference<Graph> additions,
        WeakReference<Graph> deletions,
        List<ValueChange> values) {

    public ContextDelta {
        values = List.copyOf(values);
    }

    /**
     * Creates the delta of a participant that holds triples.
     *
     * @param participant which part of the workspace changed
     * @param additions the triples the commit added
     * @param deletions the triples the commit removed
     * @return the delta
     */
    public static ContextDelta ofTriples(
            ParticipantId participant, Graph additions, Graph deletions) {
        return new ContextDelta(
                participant,
                new WeakReference<>(additions),
                new WeakReference<>(deletions),
                List.of());
    }

    /**
     * Creates the delta of a participant that holds named values.
     *
     * @param participant which part of the workspace changed
     * @param values the values the commit changed
     * @return the delta
     */
    public static ContextDelta ofValues(ParticipantId participant, List<ValueChange> values) {
        return new ContextDelta(participant, null, null, values);
    }

    /** Returns the kind of data that changed, named the way the changelog shows it. */
    public String contextName() {
        return participant.kindName();
    }

    /** Returns the graph this delta belongs to, or {@code null} for workspace-wide data. */
    public String graphUri() {
        return participant.scope();
    }

    /**
     * Returns the same delta seen under a different identifier, for an entry rendered with the
     * participants identified as they stand now rather than as they stood when it was recorded.
     *
     * @param currentId the identifier the participant has now
     * @return the delta under {@code currentId}
     */
    public ContextDelta identifiedAs(ParticipantId currentId) {
        return new ContextDelta(currentId, additions, deletions, values);
    }
}
