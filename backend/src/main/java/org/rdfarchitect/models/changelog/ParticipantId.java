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
 * Describes what a transaction participant represents, so that a changelog entry can state which
 * part of the workspace a commit touched without holding on to the participant itself.
 *
 * @param kind the kind of data the participant holds
 * @param scope the graph URI for graph-scoped participants, or {@code null} for workspace-scoped
 *     ones
 */
public record ParticipantId(Kind kind, String scope) {

    /** The kinds of data that take part in a workspace transaction. */
    public enum Kind {
        /** The RDF schema of a graph. */
        RDF,
        /** The custom SHACL shapes of a graph. */
        SHACL,
        /** Diagram layout information. */
        DL,
        /** The collection of custom diagrams of a graph or of the workspace. */
        DIAGRAMS,
        /** The namespace prefix mapping of the workspace. */
        PREFIXES,
        /** The set of graphs the workspace contains. */
        GRAPHS
    }

    /**
     * Creates an identifier for a participant that belongs to a single graph.
     *
     * @param kind the kind of data the participant holds
     * @param graphUri the URI of the graph the participant belongs to
     * @return the identifier
     */
    public static ParticipantId ofGraph(Kind kind, String graphUri) {
        return new ParticipantId(kind, graphUri);
    }

    /**
     * Creates an identifier for a participant that belongs to the workspace as a whole.
     *
     * @param kind the kind of data the participant holds
     * @return the identifier
     */
    public static ParticipantId ofWorkspace(Kind kind) {
        return new ParticipantId(kind, null);
    }

    /** Returns whether this participant belongs to a single graph rather than to the workspace. */
    public boolean isGraphScoped() {
        return scope != null;
    }
}
