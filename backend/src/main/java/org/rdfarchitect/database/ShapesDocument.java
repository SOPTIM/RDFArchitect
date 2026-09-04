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

package org.rdfarchitect.database;

import lombok.Getter;
import lombok.Setter;

import org.rdfarchitect.rdf.graph.wrapper.RDFGraphDelta;

import java.util.UUID;

/**
 * One set of SHACL shapes belonging to a graph — typically an official constraints file that was
 * imported, or a set of shapes authored in RDFArchitect.
 *
 * <p>A graph holds any number of these. They are kept apart rather than merged into one shapes
 * graph so that each can be replaced, exported, validated and reported on individually — merging
 * would make it impossible to say which file a constraint came from, or to hand an imported file
 * back unchanged.
 *
 * <h2>Why the raw text is authoritative</h2>
 *
 * <p>{@link #getRawText()} is the content as the user last saw it; {@link #getGraph()} is what
 * parsing that text produced. Official ENTSO-E constraint files carry comments and a deliberate
 * ordering that users expect to get back byte-for-byte, and reporting a validation finding at a
 * line and column is only possible against the original text — a Jena round-trip destroys both.
 *
 * <p>The graph is the undoable half: it takes part in the context's transactions and history like
 * any other participant. The raw text is not independently versioned, so an undo re-derives it from
 * the graph rather than leaving the two disagreeing. Comments and formatting are therefore lost
 * across an undo, which is a narrower loss than letting the text drift out of sync with the shapes
 * actually stored.
 */
@Getter
public class ShapesDocument {

    /** Where a document came from, which decides how carefully its formatting is preserved. */
    public enum Origin {
        /** Uploaded from a file — treat its text as something the user wants back unchanged. */
        IMPORTED,
        /** Authored in RDFArchitect. */
        AUTHORED
    }

    private final UUID id;

    private final Origin origin;

    /** Parsed shapes; the undoable half of the document. */
    private final RDFGraphDelta graph;

    /** Display name, unique within a graph. */
    @Setter private String name;

    /** File the document was uploaded from, or {@code null} when it was authored here. */
    @Setter private String sourceFileName;

    /** Whether the shapes take part in validation and combined export. */
    @Setter private boolean enabled = true;

    /** Position in the graph's document list, and the order shapes are merged for export. */
    @Setter private int order;

    /** Verbatim source text; see the class comment on why this is authoritative. */
    @Setter private String rawText;

    public ShapesDocument(UUID id, String name, Origin origin, RDFGraphDelta graph) {
        this.id = id;
        this.name = name;
        this.origin = origin;
        this.graph = graph;
    }
}
